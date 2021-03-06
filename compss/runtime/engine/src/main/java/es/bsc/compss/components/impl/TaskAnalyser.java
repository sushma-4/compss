/*
 *  Copyright 2002-2019 Barcelona Supercomputing Center (www.bsc.es)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package es.bsc.compss.components.impl;

import es.bsc.compss.api.TaskMonitor;
import es.bsc.compss.components.monitor.impl.EdgeType;
import es.bsc.compss.components.monitor.impl.GraphGenerator;
import es.bsc.compss.log.Loggers;
import es.bsc.compss.types.AbstractTask;
import es.bsc.compss.types.CommutativeGroupTask;
import es.bsc.compss.types.CommutativeIdentifier;
import es.bsc.compss.types.Task;
import es.bsc.compss.types.TaskDescription;
import es.bsc.compss.types.TaskGroup;
import es.bsc.compss.types.TaskState;
import es.bsc.compss.types.annotations.parameter.DataType;
import es.bsc.compss.types.annotations.parameter.OnFailure;
import es.bsc.compss.types.data.DataAccessId;
import es.bsc.compss.types.data.DataAccessId.Direction;
import es.bsc.compss.types.data.DataInfo;
import es.bsc.compss.types.data.DataInstanceId;
import es.bsc.compss.types.data.accessid.RAccessId;
import es.bsc.compss.types.data.accessid.RWAccessId;
import es.bsc.compss.types.data.accessid.WAccessId;
import es.bsc.compss.types.data.accessparams.AccessParams.AccessMode;
import es.bsc.compss.types.data.operation.ResultListener;
import es.bsc.compss.types.implementations.TaskType;
import es.bsc.compss.types.parameter.BindingObjectParameter;
import es.bsc.compss.types.parameter.CollectionParameter;
import es.bsc.compss.types.parameter.DependencyParameter;
import es.bsc.compss.types.parameter.ExternalPSCOParameter;
import es.bsc.compss.types.parameter.ExternalStreamParameter;
import es.bsc.compss.types.parameter.FileParameter;
import es.bsc.compss.types.parameter.ObjectParameter;
import es.bsc.compss.types.parameter.Parameter;
import es.bsc.compss.types.parameter.StreamParameter;
import es.bsc.compss.types.request.ap.BarrierGroupRequest;
import es.bsc.compss.types.request.ap.BarrierRequest;
import es.bsc.compss.types.request.ap.CancelApplicationTasksRequest;
import es.bsc.compss.types.request.ap.EndOfAppRequest;
import es.bsc.compss.types.request.ap.WaitForConcurrentRequest;
import es.bsc.compss.types.request.ap.WaitForTaskRequest;
import es.bsc.compss.util.ErrorManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import storage.StubItf;


/**
 * Class to analyze the data dependencies between tasks.
 */
public class TaskAnalyser {

    // Logger
    private static final Logger LOGGER = LogManager.getLogger(Loggers.TA_COMP);
    private static final boolean DEBUG = LOGGER.isDebugEnabled();
    private static final String TASK_FAILED = "Task failed: ";
    private static final String TASK_CANCELED = "Task canceled: ";

    // Components
    private DataInfoProvider dip;
    private GraphGenerator gm;

    // Map: data Id -> WritersInfo
    private Map<Integer, WritersInfo> writers;
    // Method information
    private Map<Integer, Integer> currentTaskCount;
    // Map: app id -> task count
    private Map<Long, Integer> appIdToTotalTaskCount;
    // Map: app id -> task count
    private Map<Long, Integer> appIdToTaskCount;
    // Map: app id -> semaphore to notify end of app
    private Map<Long, Semaphore> appIdToSemaphore;
    // List of appIds stopped on a barrier synchronization point
    private Set<Long> appIdBarrierFlags;
    // Map: app id -> set of written data ids (for result files)
    private Map<Long, Set<Integer>> appIdToWrittenFiles;
    // Map: app id -> set of written data ids (for result SCOs)
    private Map<Long, Set<Integer>> appIdToSCOWrittenIds;
    // Tasks being waited on: taskId -> list of semaphores where to notify end of task
    private Hashtable<AbstractTask, List<Semaphore>> waitedTasks;
    // Concurrent tasks being waited on: taskId -> semaphore where to notify end of task
    private Map<Integer, List<Task>> concurrentAccessMap;
    // Tasks that are accessed commutatively. Map: data id -> commutative group tasks
    private Map<String, CommutativeGroupTask> commutativeGroup;
    // Tasks that are accessed commutatively and are pending to be drawn in graph. Map: commutative group identifier ->
    // list of tasks from group
    private Map<String, LinkedList<Task>> pendingToDrawCommutative;
    // Task groups. Map: group name -> commutative group tasks
    private Map<Long, TreeMap<String, TaskGroup>> taskGroups;
    // Registered task groups
    private Map<Long, Stack<TaskGroup>> currentTaskGroups;

    // Graph drawing
    private static final boolean IS_DRAW_GRAPH = GraphGenerator.isEnabled();
    private int synchronizationId;
    private boolean taskDetectedAfterSync;


    /**
     * Creates a new Task Analyzer instance.
     */
    public TaskAnalyser() {
        this.currentTaskCount = new HashMap<>();
        this.writers = new TreeMap<>();

        this.appIdToTaskCount = new HashMap<>();
        this.appIdToTotalTaskCount = new HashMap<>();
        this.appIdToSemaphore = new HashMap<>();
        this.appIdBarrierFlags = new HashSet<>();
        this.appIdToWrittenFiles = new HashMap<>();
        this.appIdToSCOWrittenIds = new HashMap<>();

        this.waitedTasks = new Hashtable<>();
        this.concurrentAccessMap = new TreeMap<>();
        this.commutativeGroup = new TreeMap<>();
        this.pendingToDrawCommutative = new TreeMap<>();
        this.currentTaskGroups = new HashMap<>();
        this.taskGroups = new HashMap<>();
        this.synchronizationId = 0;
        this.taskDetectedAfterSync = false;

        LOGGER.info("Initialization finished");
    }

    /**
     * Sets the TaskAnalyser co-workers.
     *
     * @param dip DataInfoProvider co-worker.
     */
    public void setCoWorkers(DataInfoProvider dip) {
        this.dip = dip;
    }

    /**
     * Sets the graph generator co-worker.
     *
     * @param gm Graph Generator co-worker.
     */
    public void setGM(GraphGenerator gm) {
        this.gm = gm;

        // Add initial synchronization point
        if (IS_DRAW_GRAPH) {
            this.gm.addSynchroToGraph(0);
        }
    }

    /**
     * Process the dependencies of a new task {@code currentTask}.
     *
     * @param currentTask Task.
     */
    public void processTask(Task currentTask) {
        TaskDescription params = currentTask.getTaskDescription();
        LOGGER.info("New " + (params.getType() == TaskType.METHOD ? "method" : "service") + " task(" + params.getName()
            + "), ID = " + currentTask.getId());

        if (IS_DRAW_GRAPH) {
            addNewTask(currentTask);
        }

        // Update task count
        Integer methodId = params.getCoreElement().getCoreId();
        Integer actualCount = this.currentTaskCount.get(methodId);
        if (actualCount == null) {
            actualCount = 0;
        }
        this.currentTaskCount.put(methodId, actualCount + 1);

        // Update app id task count
        Long appId = currentTask.getAppId();
        Integer taskCount = this.appIdToTaskCount.get(appId);
        if (taskCount == null) {
            taskCount = 0;
        }
        taskCount++;
        this.appIdToTaskCount.put(appId, taskCount);
        Integer totalTaskCount = this.appIdToTotalTaskCount.get(appId);
        if (totalTaskCount == null) {
            totalTaskCount = 0;
        }
        totalTaskCount++;
        this.appIdToTotalTaskCount.put(appId, totalTaskCount);

        // Check scheduling enforcing data
        int constrainingParam = -1;
        if (params.getType() == TaskType.SERVICE && params.hasTargetObject()) {
            constrainingParam = params.getParameters().size() - 1 - params.getNumReturns();
        }

        // Set task group
        if (!applicationHasGroups(currentTask.getAppId())) {
            setCurrentTaskGroup("App" + currentTask.getAppId(), true, currentTask.getAppId());
        }
        Iterator<TaskGroup> currentGroups = this.currentTaskGroups.get(currentTask.getAppId()).iterator();
        while (currentGroups.hasNext()) {
            TaskGroup nextGroup = currentGroups.next();
            currentTask.setTaskGroup(nextGroup);
            nextGroup.addTask(currentTask);
        }

        // Process parameters
        List<Parameter> parameters = params.getParameters();
        boolean taskHasEdge = false;
        for (int paramIdx = 0; paramIdx < parameters.size(); paramIdx++) {
            boolean isConstraining = paramIdx == constrainingParam;
            boolean paramHasEdge =
                registerParameterAccessAndAddDependencies(currentTask, parameters.get(paramIdx), isConstraining);
            taskHasEdge = taskHasEdge || paramHasEdge;
        }
        if (IS_DRAW_GRAPH) {
            if (!taskHasEdge) {
                // If the graph must be written and the task has no edge due to its parameters,
                // add a direct dependency from last sync to task.
                addEdgeFromMainToTask(currentTask);
            }
        }
    }

    /**
     * Returns the tasks dependent to the requested task.
     *
     * @param request Requested task.
     */
    public void findWaitedTask(WaitForTaskRequest request) {
        int dataId = request.getDataId();
        AccessMode am = request.getAccessMode();
        Semaphore sem = request.getSemaphore();

        // Retrieve writers information
        WritersInfo wi = this.writers.get(dataId);
        if (wi != null) {
            switch (wi.getDataType()) {
                case STREAM_T:
                case EXTERNAL_STREAM_T:
                    // Mark the data accesses
                    List<AbstractTask> lastStreamWriters = wi.getStreamWriters();
                    for (AbstractTask lastWriter : lastStreamWriters) {
                        treatDataAccess(lastWriter, am, dataId);
                    }
                    // We do not wait for stream task to complete
                    sem.release();
                    break;
                default:
                    // Retrieve last writer task
                    AbstractTask lastWriter = wi.getDataWriter();
                    // Mark the data access
                    if (lastWriter != null) {
                        treatDataAccess(lastWriter, am, dataId);
                    }
                    // Release task if possible. Otherwise add to waiting
                    if (lastWriter == null || lastWriter.getStatus() == TaskState.FINISHED
                        || lastWriter.getStatus() == TaskState.CANCELED || lastWriter.getStatus() == TaskState.FAILED) {
                        sem.release();
                    } else {
                        List<Semaphore> list = this.waitedTasks.get(lastWriter);
                        if (list == null) {
                            list = new LinkedList<>();
                        }
                        list.add(sem);
                        this.waitedTasks.put(lastWriter, list);
                    }
                    break;
            }
        } else {
            // No writer registered, release
            sem.release();
        }
    }

    /**
     * Registers the end of execution of task @{code task}.
     *
     * @param aTask Ended task.
     */
    public void endTask(AbstractTask aTask) {
        if (aTask instanceof Task) {
            Task task = (Task) aTask;
            int taskId = task.getId();
            boolean isFree = task.isFree();
            TaskState taskState = task.getStatus();
            OnFailure onFailure = task.getOnFailure();
            LOGGER.info("Notification received for task " + taskId + " with end status " + taskState);

            // Check status
            if (!isFree) {
                LOGGER.debug("Task " + taskId + " is not registered as free. Waiting for other executions to end");
                return;
            }
            TaskMonitor registeredMonitor = task.getTaskMonitor();
            switch (taskState) {
                case FAILED:
                    registeredMonitor.onFailure();
                    if (onFailure == OnFailure.RETRY || onFailure == OnFailure.FAIL) {
                        ErrorManager.error(TASK_FAILED + task);
                        return;
                    }
                    if (onFailure == OnFailure.IGNORE || onFailure == OnFailure.CANCEL_SUCCESSORS) {
                        // Show warning
                        ErrorManager.warn(TASK_FAILED + task);
                    }
                    break;
                case CANCELED:
                    registeredMonitor.onCancellation();

                    // Show warning
                    ErrorManager.warn(TASK_CANCELED + task);
                    break;
                default:
                    registeredMonitor.onCompletion();
            }

            // Free barrier dependencies
            if (DEBUG) {
                LOGGER.debug("Freeing barriers for task " + taskId);
                LOGGER.debug("Ending task " + taskId);
            }

            // Free dependencies
            Long appId = task.getAppId();
            Integer taskCount = this.appIdToTaskCount.get(appId) - 1;
            this.appIdToTaskCount.put(appId, taskCount);
            if (taskCount == 0) {
                // Remove the appId from the barrier flags (if existent, otherwise do nothing)
                this.appIdBarrierFlags.remove(appId);
                Semaphore sem = this.appIdToSemaphore.remove(appId);
                if (sem != null) {
                    // Application was synchronized on a barrier flag or a no more tasks
                    // Release the application semaphore
                    this.appIdToTaskCount.remove(appId);
                    sem.release();
                }
            }

            // Free task data dependencies
            if (DEBUG) {
                LOGGER.debug("Releasing waiting tasks for task " + taskId);
            }
            List<Semaphore> sems = this.waitedTasks.remove(task);
            if (sems != null) {
                for (Semaphore sem : sems) {
                    sem.release();
                }
            }

            // Mark parameter accesses
            if (DEBUG) {
                LOGGER.debug("Marking accessed parameters for task " + taskId);
            }
            for (Parameter param : task.getTaskDescription().getParameters()) {
                updateParameterAccess(task, param);
            }

            // Check if the finished task was the last writer of a file, but only if task generation has finished
            // Task generation is finished if we are on noMoreTasks but we are not on a barrier
            if (DEBUG) {
                LOGGER.debug("Checking result file transfers for task " + taskId);
            }
            if (this.appIdToSemaphore.get(appId) != null && !this.appIdBarrierFlags.contains(appId)) {
                checkResultFileTransfer(task);
            }

            // Release data dependent tasks
            if (DEBUG) {
                LOGGER.debug("Releasing data dependant tasks for task " + taskId);
            }

            // Release task groups of the task
            releaseTaskGroups(task);

            // Releases commutative groups dependent and releases all the waiting tasks
            releaseCommutativeGroups(task);
        }

        // Release data dependent tasks
        aTask.releaseDataDependents();
    }

    /**
     * Returns the tasks state.
     *
     * @return A string representation of the tasks state.
     */
    public String getTaskStateRequest() {
        StringBuilder sb = new StringBuilder("\t").append("<TasksInfo>").append("\n");
        for (Entry<Long, Integer> e : this.appIdToTotalTaskCount.entrySet()) {
            Long appId = e.getKey();
            Integer totalTaskCount = e.getValue();
            Integer taskCount = this.appIdToTaskCount.get(appId);
            if (taskCount == null) {
                taskCount = 0;
            }
            int completed = totalTaskCount - taskCount;
            sb.append("\t\t").append("<Application id=\"").append(appId).append("\">").append("\n");
            sb.append("\t\t\t").append("<TotalCount>").append(totalTaskCount).append("</TotalCount>").append("\n");
            sb.append("\t\t\t").append("<InProgress>").append(taskCount).append("</InProgress>").append("\n");
            sb.append("\t\t\t").append("<Completed>").append(completed).append("</Completed>").append("\n");
            sb.append("\t\t").append("</Application>").append("\n");
        }
        sb.append("\t").append("</TasksInfo>").append("\n");
        return sb.toString();
    }

    /**
     * Barrier.
     *
     * @param request Barrier request.
     */
    public void barrier(BarrierRequest request) {
        Long appId = request.getAppId();
        Integer count = this.appIdToTaskCount.get(appId);
        if (IS_DRAW_GRAPH) {
            // Addition of missing commutative groups to graph
            addMissingCommutativeTasksToGraph();
            addNewBarrier();

            // We can draw the graph on a barrier while we wait for tasks
            this.gm.commitGraph();
        }

        // Release the semaphore only if all application tasks have finished
        if (count == null || count == 0) {
            request.getSemaphore().release();
        } else {
            this.appIdBarrierFlags.add(appId);
            this.appIdToSemaphore.put(appId, request.getSemaphore());
        }
    }

    /**
     * End of execution barrier.
     *
     * @param request End of execution request.
     */
    public void noMoreTasks(EndOfAppRequest request) {
        Long appId = request.getAppId();
        Integer count = this.appIdToTaskCount.get(appId);

        if (IS_DRAW_GRAPH) {
            addMissingCommutativeTasksToGraph();
            this.gm.commitGraph();
        }

        if (count == null || count == 0) {
            this.appIdToTaskCount.remove(appId);
            request.getSemaphore().release();
        } else {
            this.appIdToSemaphore.put(appId, request.getSemaphore());
        }
    }

    /**
     * Deletes the specified data and its renamings.
     *
     * @param dataInfo DataInfo.
     */
    public void deleteData(DataInfo dataInfo) {
        int dataId = dataInfo.getDataId();
        LOGGER.info("Deleting data " + dataId);
        WritersInfo wi = this.writers.remove(dataId);
        if (wi != null) {
            switch (wi.getDataType()) {
                case STREAM_T:
                case EXTERNAL_STREAM_T:
                    // No data to delete
                    break;
                case FILE_T:
                    // Remove file data form the list of written files
                    for (Set<Integer> files : this.appIdToWrittenFiles.values()) {
                        if (files.remove(dataId)) {
                            LOGGER.info(" Removed data " + dataId + " from written files");
                        }
                    }
                    break;
                case PSCO_T:
                    // Remove PSCO data from the list of written PSCO
                    for (Set<Integer> pscos : this.appIdToSCOWrittenIds.values()) {
                        if (pscos.remove(dataId)) {
                            LOGGER.info(" Removed data " + dataId + " from written pscos");
                        }
                    }
                    break;
                default:
                    // Nothing to do for other types
                    break;
            }
        } else {
            LOGGER.warn("Writters info for data " + dataId + " not found.");
        }
    }

    /**
     * Returns whether a given applicationhas groups registered or not.
     *
     * @param appId Application Id.
     * @return {@literal true} if the application has registered groups, {@literal false} otherwise.
     */
    public boolean applicationHasGroups(Long appId) {
        return this.currentTaskGroups.containsKey(appId);
    }

    /**
     * Cancels tasks of a given application.
     *
     * @param request Cancel application tasks request.
     */
    public void cancelApplicationTasks(CancelApplicationTasksRequest request) {
        LOGGER.debug("Cancelling tasks of application " + request.getAppId());
        Semaphore sem = request.getSemaphore();
        Long appId = request.getAppId();
        String groupName = "App" + appId;

        if (this.taskGroups.containsKey(appId) && this.taskGroups.get(appId).containsKey(groupName)) {
            TaskGroup tg = this.taskGroups.get(appId).get(groupName);
            tg.cancelTasks();
            this.taskGroups.remove(appId);
        }
        sem.release();
    }

    /**
     * Returns the written files and deletes them.
     *
     * @param appId Application id.
     * @return List of written files of the application.
     */
    public Set<Integer> getAndRemoveWrittenFiles(Long appId) {
        return this.appIdToWrittenFiles.remove(appId);
    }

    /**
     * Shutdown the component.
     */
    public void shutdown() {
        if (IS_DRAW_GRAPH) {
            GraphGenerator.removeTemporaryGraph();
        }
    }

    /*
     * ********************************************************************************************************
     * CONCURRENT PUBLIC METHODS
     **********************************************************************************************************/

    /**
     * Check whether a dataId is of type concurrent or not.
     *
     * @param daId {@code true} if the dataId is concurrent, {@code false} otherwise.
     */
    public boolean dataWasAccessedConcurrent(int daId) {
        List<Task> concurrentAccess = this.concurrentAccessMap.get(daId);
        if (concurrentAccess != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the concurrent tasks dependent to the requested task.
     *
     * @param request Requested task.
     */
    public void findWaitedConcurrent(WaitForConcurrentRequest request) {
        int dataId = request.getDataId();
        AccessMode am = request.getAccessMode();
        List<Task> concurrentAccess = this.concurrentAccessMap.get(dataId);
        if (concurrentAccess != null) {
            // Add to writers if needed
            this.concurrentAccessMap.put(dataId, null);
        }

        Semaphore semTasks = request.getTaskSemaphore();
        int n = 0;
        for (Task task : concurrentAccess) {
            treatDataAccess(task, am, dataId);
            if (task.getStatus() != TaskState.FINISHED) {
                n++;
                List<Semaphore> list = this.waitedTasks.get(task);
                if (list == null) {
                    list = new LinkedList<>();
                }
                list.add(semTasks);
                this.waitedTasks.put(task, list);
            }
        }
        request.setNumWaitedTasks(n);
        request.getSemaphore().release();
    }

    /**
     * Removes the tasks that have accessed the data in a concurrent way.
     *
     * @param dataId Data Id.
     */
    public void removeFromConcurrentAccess(int dataId) {
        List<Task> returnedValue = this.concurrentAccessMap.remove(dataId);
        if (returnedValue == null) {
            LOGGER.debug("The concurrent list could not be removed");
        }
    }

    /*
     * *************************************************************************************************************
     * TASK GROUPS PUBLIC METHODS
     ***************************************************************************************************************/

    /**
     * Sets the current task group to assign to tasks.
     * 
     * @param groupName Name of the group to set
     * @param barrier Flag stating if the group has to perform a barrier.
     */
    public void setCurrentTaskGroup(String groupName, boolean barrier, Long appId) {
        LOGGER.debug("Adding group " + groupName + " to the current groups stack.");

        if (!this.currentTaskGroups.containsKey(appId)) {
            Stack<TaskGroup> currentTaskGroups = new Stack<>();
            this.currentTaskGroups.put(appId, currentTaskGroups);
        }

        TaskGroup tg = new TaskGroup(groupName, appId);
        this.currentTaskGroups.get(appId).push(tg);
        if (!this.taskGroups.containsKey(appId)) {
            TreeMap<String, TaskGroup> taskGroups = new TreeMap<>();
            this.taskGroups.put(appId, taskGroups);
        }
        this.taskGroups.get(appId).put(groupName, tg);

        if (IS_DRAW_GRAPH) {
            if (!tg.isAppGroup(appId)) {
                this.gm.addTaskGroupToGraph(tg.getName());
                LOGGER.debug("Group " + groupName + " added to graph");
            }
            tg.setGraphDrawn();
        }
    }

    /**
     * Closes the last task group.
     */
    public void closeCurrentTaskGroup(Long appId) {
        TaskGroup tg = this.currentTaskGroups.get(appId).pop();
        tg.setClosed();
        if (IS_DRAW_GRAPH) {
            this.gm.closeGroupInGraph();
        }
    }

    private void releaseTaskGroups(Task task) {
        for (TaskGroup group : task.getTaskGroupList()) {
            group.removeTask(task);
            LOGGER.debug("Group " + group.getName() + " released a task");
            if (!group.hasPendingTasks() && group.isClosed() && group.hasBarrier()) {
                group.releaseBarrier();
                if (group.getBarrierDrawn()) {
                    this.taskGroups.get(task.getAppId()).remove(group.getName());
                    LOGGER.debug("All tasks of group " + group.getName() + " have finished execution");
                }
                LOGGER.debug("All tasks of group " + group.getName() + " have finished execution");
            }
        }
    }

    private void releaseCommutativeGroups(Task task) {
        if (!task.getCommutativeGroupList().isEmpty()) {
            for (CommutativeGroupTask group : task.getCommutativeGroupList()) {
                group.setStatus(TaskState.FINISHED);
                group.removePredecessor(task);
                if (group.getPredecessors().isEmpty()) {
                    group.releaseDataDependents();
                    // Check if task is being waited
                    List<Semaphore> sems = this.waitedTasks.remove(group);
                    if (sems != null) {
                        for (Semaphore sem : sems) {
                            sem.release();
                        }
                    }
                    if (DEBUG) {
                        LOGGER.debug("Group " + group.getId() + " ended execution");
                        LOGGER.debug("Data dependents of group " + group.getCommutativeIdentifier() + " released ");
                    }
                }
            }
        }
    }

    /**
     * Barrier for group.
     *
     * @param request Barrier group request
     */
    public void barrierGroup(BarrierGroupRequest request) {
        String groupName = request.getGroupName();
        Long appId = request.getAppId();
        TaskGroup tg = this.taskGroups.get(request.getAppId()).get(groupName);
        Integer count = this.appIdToTaskCount.get(appId);
        // Addition of missing commutative groups to graph
        if (IS_DRAW_GRAPH) {
            addMissingCommutativeTasksToGraph();
            addNewGroupBarrier(tg);
            // We can draw the graph on a barrier while we wait for tasks
            this.gm.commitGraph();
        }

        if (count == null || count == 0) {
            if (tg != null && !tg.hasPendingTasks()) {
                if (tg.hasException()) {
                    request.setException(tg.getException());
                }
                request.getSemaphore().release();
            } else {
                // Release the semaphore only if all application tasks have finished
                request.getSemaphore().release();
            }
        } else {
            if (tg != null && !tg.hasPendingTasks()) {
                if (tg.hasException()) {
                    request.setException(tg.getException());
                }
                request.getSemaphore().release();
            } else {
                tg.addBarrier(request);
            }
        }
    }

    private void addMissingCommutativeTasksToGraph() {
        LinkedList<String> identifiers = new LinkedList<String>();
        for (String identifier : this.pendingToDrawCommutative.keySet()) {
            addCommutativeGroupTaskToGraph(identifier);
            identifiers.add(identifier);
        }
        for (String identifier : identifiers) {
            this.pendingToDrawCommutative.remove(identifier);
        }
    }

    /*
     * *************************************************************************************************************
     * DATA DEPENDENCY MANAGEMENT PRIVATE METHODS
     ***************************************************************************************************************/

    private boolean registerParameterAccessAndAddDependencies(Task currentTask, Parameter p, boolean isConstraining) {
        // Conversion: direction -> access mode
        AccessMode am = AccessMode.R;
        switch (p.getDirection()) {
            case IN:
                am = AccessMode.R;
                break;
            case OUT:
                am = AccessMode.W;
                break;
            case INOUT:
                am = AccessMode.RW;
                break;
            case CONCURRENT:
                am = AccessMode.C;
                break;
            case COMMUTATIVE:
                am = AccessMode.CV;
                break;
        }

        // First DataAccess registered on a commutative group
        DataAccessId firstRegistered = null;

        // Inform the Data Manager about the new accesses
        boolean hasParamEdge = false;
        DataAccessId daId;
        switch (p.getType()) {
            case FILE_T:
                FileParameter fp = (FileParameter) p;
                daId = this.dip.registerFileAccess(am, fp.getLocation());
                break;
            case PSCO_T:
                ObjectParameter pscop = (ObjectParameter) p;
                // Check if its PSCO class and persisted to infer its type
                pscop.setType(DataType.PSCO_T);
                daId = this.dip.registerObjectAccess(am, pscop.getValue(), pscop.getCode());
                break;
            case EXTERNAL_PSCO_T:
                ExternalPSCOParameter externalPSCOparam = (ExternalPSCOParameter) p;
                // Check if its PSCO class and persisted to infer its type
                externalPSCOparam.setType(DataType.EXTERNAL_PSCO_T);
                daId = dip.registerExternalPSCOAccess(am, externalPSCOparam.getId(), externalPSCOparam.getCode());
                break;
            case BINDING_OBJECT_T:
                BindingObjectParameter bindingObjectparam = (BindingObjectParameter) p;
                // Check if its Binding OBJ and register its access
                bindingObjectparam.setType(DataType.BINDING_OBJECT_T);
                daId = dip.registerBindingObjectAccess(am, bindingObjectparam.getBindingObject(),
                    bindingObjectparam.getCode());
                break;
            case OBJECT_T:
                ObjectParameter op = (ObjectParameter) p;
                // Check if its PSCO class and persisted to infer its type
                if (op.getValue() instanceof StubItf && ((StubItf) op.getValue()).getID() != null) {
                    op.setType(DataType.PSCO_T);
                }
                daId = this.dip.registerObjectAccess(am, op.getValue(), op.getCode());
                break;
            case STREAM_T:
                StreamParameter sp = (StreamParameter) p;
                daId = this.dip.registerStreamAccess(am, sp.getValue(), sp.getCode());
                break;
            case EXTERNAL_STREAM_T:
                ExternalStreamParameter esp = (ExternalStreamParameter) p;
                daId = this.dip.registerExternalStreamAccess(am, esp.getLocation());
                break;
            case COLLECTION_T:
                CollectionParameter cp = (CollectionParameter) p;
                for (Parameter content : cp.getParameters()) {
                    boolean hasCollectionParamEdge =
                        registerParameterAccessAndAddDependencies(currentTask, content, isConstraining);
                    hasParamEdge = hasParamEdge || hasCollectionParamEdge;
                }
                daId = dip.registerCollectionAccess(am, cp);
                break;
            default:
                // This is a basic type, there are no accesses to register
                daId = null;
        }

        if (daId != null) {
            // Add parameter dependencies
            DependencyParameter dp = (DependencyParameter) p;
            if (am == AccessMode.CV) {
                // Register commutative access
                Integer coreId = currentTask.getTaskDescription().getCoreElement().getCoreId();
                CommutativeIdentifier comId = new CommutativeIdentifier(coreId, daId.getDataId());
                CommutativeGroupTask com = null;
                for (CommutativeGroupTask cgt : this.commutativeGroup.values()) {
                    if (cgt.getCommutativeIdentifier().compareTo(comId) == 1) {
                        com = cgt;
                    }
                }
                if (com == null) {
                    firstRegistered = daId;
                    LOGGER.debug(
                        "The FIRST registered daId in the commutative group " + comId.toString() + " is " + daId);
                } else {
                    com.addVersionToList(daId);
                    daId = com.getRegisteredVersion();
                    LOGGER.debug("Registering daId " + daId + " in commutative group " + comId.toString());
                }

                dp.setDataAccessId(daId);
                hasParamEdge = addCommutativeDependencies(currentTask, dp, firstRegistered, coreId);
            } else {
                // Register regular access
                dp.setDataAccessId(daId);
                hasParamEdge = addDependencies(am, currentTask, isConstraining, dp, firstRegistered);
            }
        } else {
            // Basic types do not produce access dependencies
        }

        // Return data Id
        return hasParamEdge;
    }

    private boolean addDependencies(AccessMode am, Task currentTask, boolean isConstraining, DependencyParameter dp,
        DataAccessId firstRegistered) {

        // Add dependencies to the graph and register output values for future dependencies
        boolean hasParamEdge = false;
        DataAccessId daId = dp.getDataAccessId();
        switch (am) {
            case R:
                if (!dataWasAccessedConcurrent(daId.getDataId())) {
                    hasParamEdge = checkDependencyForRead(currentTask, dp);
                } else {
                    hasParamEdge = checkDependencyForConcurrent(currentTask, dp);
                }
                if (isConstraining) {
                    RAccessId raId = (RAccessId) dp.getDataAccessId();
                    DataInstanceId dependingDataId = raId.getReadDataInstance();
                    if (dependingDataId != null) {
                        if (dependingDataId.getVersionId() > 1) {
                            WritersInfo wi = this.writers.get(dependingDataId.getDataId());
                            if (wi != null) {
                                switch (wi.getDataType()) {
                                    case STREAM_T:
                                    case EXTERNAL_STREAM_T:
                                        // Retrieve all the stream writers and enforce the execution to be near any
                                        List<AbstractTask> lastWriters = wi.getStreamWriters();
                                        if (!lastWriters.isEmpty()) {
                                            currentTask.setEnforcingTask((Task) (lastWriters.get(0)));
                                        }
                                        break;
                                    default:
                                        // Retrieve the writer and enforce the execution to be near the writer task
                                        AbstractTask lastWriter = wi.getDataWriter();
                                        if (lastWriter != null) {
                                            currentTask.setEnforcingTask((Task) lastWriter);
                                        }
                                        break;
                                }
                            }
                        }
                    }
                }
                break;
            case RW:
                if (!dataWasAccessedConcurrent(daId.getDataId())) {
                    hasParamEdge = checkDependencyForRead(currentTask, dp);
                } else {
                    hasParamEdge = checkDependencyForConcurrent(currentTask, dp);
                    removeFromConcurrentAccess(dp.getDataAccessId().getDataId());
                }
                if (isConstraining) {
                    RWAccessId raId = (RWAccessId) dp.getDataAccessId();
                    DataInstanceId dependingDataId = raId.getReadDataInstance();
                    if (dependingDataId != null) {
                        if (dependingDataId.getVersionId() > 1) {
                            WritersInfo wi = this.writers.get(dependingDataId.getDataId());
                            if (wi != null) {
                                switch (wi.getDataType()) {
                                    case STREAM_T:
                                        // Retrieve all the stream writers and enforce the execution to be near any
                                        List<AbstractTask> lastWriters = wi.getStreamWriters();
                                        if (!lastWriters.isEmpty()) {
                                            currentTask.setEnforcingTask((Task) lastWriters.get(0));
                                        }
                                        break;
                                    default:
                                        // Retrieve the writer and enforce the execution to be near the writer task
                                        AbstractTask lastWriter = wi.getDataWriter();
                                        if (lastWriter != null) {
                                            currentTask.setEnforcingTask((Task) lastWriter);
                                        }
                                        break;
                                }
                            }
                        }
                    }
                }
                registerOutputValues(currentTask, dp);
                break;
            case W:
                // Check concurrent
                if (dataWasAccessedConcurrent(daId.getDataId())) {
                    removeFromConcurrentAccess(dp.getDataAccessId().getDataId());
                }
                // Register output values
                registerOutputValues(currentTask, dp);
                break;
            case C:
                hasParamEdge = checkDependencyForRead(currentTask, dp);
                List<Task> tasks = this.concurrentAccessMap.get(daId.getDataId());
                if (tasks == null) {
                    tasks = new LinkedList<Task>();
                    this.concurrentAccessMap.put(daId.getDataId(), tasks);
                }
                tasks.add(currentTask);
                break;
            case CV:
                // Commutative accesses are processed in addCommutativeDependencies
                break;
        }

        return hasParamEdge;
    }

    private boolean checkDependencyForRead(Task currentTask, DependencyParameter dp) {
        int dataId = dp.getDataAccessId().getDataId();

        if (DEBUG) {
            LOGGER.debug("Checking READ dependency for datum " + dataId + " and task " + currentTask.getId());
        }

        WritersInfo wi = this.writers.get(dataId);
        if (wi != null) {
            switch (wi.getDataType()) {
                case STREAM_T:
                case EXTERNAL_STREAM_T:
                    addStreamDependency(currentTask, dp, wi);
                    break;
                default:
                    addRegularDependency(currentTask, dp, wi);
                    break;
            }
        } else {
            // Task is free
            if (DEBUG) {
                LOGGER.debug("There is no last writer for datum " + dataId);
            }
            if (IS_DRAW_GRAPH) {
                // Add edge from last sync point to task
                drawEdges(currentTask, dp, null);
            }
        }

        // A read dependency is always written in the task graph
        return true;
    }

    private boolean checkDependencyForCommutative(Task currentTask, DependencyParameter dp,
        CommutativeGroupTask commutativeGroup) {

        // Addition of a dependency to the task which generates commutative data
        AbstractTask t = commutativeGroup.getParentDataDependency();
        if (t != null) {
            LOGGER.debug("Adding dependency with parent task of commutative group");
            currentTask.addDataDependency(t);
        }
        if (IS_DRAW_GRAPH) {
            drawEdges(currentTask, dp, t);
        }

        // Addition of a dependency between the task and the commutative group
        commutativeGroup.addDataDependency(currentTask);
        commutativeGroup.addCommutativeTask(currentTask);
        currentTask.setCommutativeGroup(commutativeGroup, dp.getDataAccessId());

        // A commutative dependency is always written in the task graph
        return true;
    }

    /**
     * Checks the concurrent dependencies of a task {@code currentTask} considering the parameter {@code dp}.
     *
     * @param currentTask Task.
     * @param dp Dependency Parameter.
     */
    private boolean checkDependencyForConcurrent(Task currentTask, DependencyParameter dp) {
        int dataId = dp.getDataAccessId().getDataId();
        List<Task> tasks = this.concurrentAccessMap.get(dataId);
        if (!tasks.contains(currentTask)) {
            if (DEBUG) {
                LOGGER.debug("There was a concurrent access for datum " + dataId);
                LOGGER.debug("Adding dependency between list and task " + currentTask.getId());
            }
            for (Task t : tasks) {
                // Add dependency
                currentTask.addDataDependency(t);
                if (IS_DRAW_GRAPH) {
                    drawEdges(currentTask, dp, t);
                }
            }
        } else {
            if (DEBUG) {
                LOGGER.debug("There is no last writer for datum " + dataId);
            }
            // Add dependency to last sync point
            if (IS_DRAW_GRAPH) {
                drawEdges(currentTask, dp, null);
            }
        }

        // A concurrent dependency is always written in the task graph
        return true;
    }

    private void addRegularDependency(Task currentTask, DependencyParameter dp, WritersInfo wi) {
        int dataId = dp.getDataAccessId().getDataId();
        AbstractTask lastWriter = wi.getDataWriter();
        if (lastWriter != null && lastWriter != currentTask) {
            if (DEBUG) {
                LOGGER.debug("Last writer for datum " + dataId + " is task " + lastWriter.getId());
                LOGGER
                    .debug("Adding dependency between task " + lastWriter.getId() + " and task " + currentTask.getId());
            }

            if (lastWriter instanceof Task
                && ((Task) lastWriter).getCommutativeGroup(dp.getDataAccessId().getDataId()) != null) {
                currentTask
                    .addDataDependency(((Task) lastWriter).getCommutativeGroup(dp.getDataAccessId().getDataId()));
            }
            // Add dependency
            currentTask.addDataDependency(lastWriter);
        } else {
            // Task is free
            if (DEBUG) {
                LOGGER.debug("There is no last writer for datum " + dataId);
            }
        }

        // Add edge to graph
        if (IS_DRAW_GRAPH) {
            drawEdges(currentTask, dp, lastWriter);
            checkIfPreviousGroupInGraph(dataId, currentTask);
        }
    }

    private void addStreamDependency(Task currentTask, DependencyParameter dp, WritersInfo wi) {
        int dataId = dp.getDataAccessId().getDataId();
        List<AbstractTask> lastStreamWriters = wi.getStreamWriters();
        if (!lastStreamWriters.isEmpty()) {
            if (DEBUG) {
                StringBuilder sb = new StringBuilder();
                if (lastStreamWriters.size() > 1) {
                    sb.append("Last writers for stream datum ");
                    sb.append(dataId);
                    sb.append(" are tasks ");
                } else {
                    sb.append("Last writer for stream datum ");
                    sb.append(dataId);
                    sb.append(" is task ");
                }
                for (AbstractTask lastWriter : lastStreamWriters) {
                    sb.append(lastWriter.getId());
                    sb.append(" ");
                }
                LOGGER.debug(sb.toString());
            }

            // Add dependencies
            for (AbstractTask lastWriter : lastStreamWriters) {
                // Debug message
                if (DEBUG) {
                    LOGGER.debug("Adding stream dependency between task " + lastWriter.getId() + " and task "
                        + currentTask.getId());
                }

                // Add dependency
                currentTask.addStreamDataDependency(lastWriter);
            }
        } else {
            // Task is free
            if (DEBUG) {
                LOGGER.debug("There is no last stream writer for datum " + dataId);
            }
        }

        // Add edge to graph
        if (IS_DRAW_GRAPH) {
            drawStreamEdge(currentTask, dp, false);
            checkIfPreviousGroupInGraph(dataId, currentTask);
        }
    }

    private boolean addCommutativeDependencies(Task currentTask, DependencyParameter dp, DataAccessId firstRegistered,
        int coreId) {

        // Add dependencies to the graph and register output values for future dependencies
        DataAccessId daId = dp.getDataAccessId();
        CommutativeIdentifier comId = new CommutativeIdentifier(coreId, daId.getDataId());
        CommutativeGroupTask com = null;
        LinkedList<Task> pendingToDraw = null;
        for (CommutativeGroupTask cgt : this.commutativeGroup.values()) {
            if (cgt.getCommutativeIdentifier().compareTo(comId) == 1) {
                com = cgt;
                if (IS_DRAW_GRAPH) {
                    pendingToDraw = this.pendingToDrawCommutative.get(comId.toString());
                }
            }
        }
        if (IS_DRAW_GRAPH) {
            if (pendingToDraw == null) {
                pendingToDraw = new LinkedList<>();
            }
            pendingToDraw.add(currentTask);
            this.pendingToDrawCommutative.put(comId.toString(), pendingToDraw);
        }
        if (com == null) {
            LOGGER.info("Creating a new commutative group " + comId);
            com = new CommutativeGroupTask(currentTask.getAppId(), comId);

            if (IS_DRAW_GRAPH) {
                LOGGER.debug("Checking if previous group in graph");
                checkIfPreviousGroupInGraph(daId.getDataId(), currentTask);
            }
            WritersInfo wi = this.writers.get(daId.getDataId());
            if (wi != null) {
                AbstractTask predecessor = wi.getDataWriter();
                com.setParentDataDependency(predecessor);
                LOGGER.debug("Setting parent data dependency");
            }
            this.commutativeGroup.put(comId.toString(), com);
            com.setRegisteredVersion(firstRegistered);
            registerOutputValues(com, dp);

        }

        com.setFinalVersion(((RWAccessId) daId).getWVersionId());
        boolean hasParamEdge = checkDependencyForCommutative(currentTask, dp, com);
        registerOutputValues(com, dp);

        return hasParamEdge;
    }

    private void treatDataAccess(AbstractTask lastWriter, AccessMode am, int dataId) {
        // Add to writers if needed
        if (am == AccessMode.RW) {
            WritersInfo wi = this.writers.get(dataId);
            if (wi != null) {
                switch (wi.getDataType()) {
                    case STREAM_T:
                    case EXTERNAL_STREAM_T:
                        // Nothing to do, we do not reset the writers because of the main access
                        break;
                    default:
                        // Reset the writers entry
                        wi.setDataWriter(null);
                        break;
                }
            } else {
                // Add a new reset entry
                LOGGER.warn("Adding null writer info for data " + dataId);
                this.writers.put(dataId, null);
            }
        }

        // Add graph description
        if (IS_DRAW_GRAPH) {
            TreeSet<Integer> toPass = new TreeSet<>();
            toPass.add(dataId);
            DataInstanceId dii = this.dip.getLastVersions(toPass).get(0);
            int dataVersion = dii.getVersionId();
            addEdgeFromTaskToMain(lastWriter, EdgeType.DATA_DEPENDENCY, dataId, dataVersion);
        }
    }

    private void updateParameterAccess(Task t, Parameter p) {
        DataType type = p.getType();

        if (type == DataType.COLLECTION_T) {
            for (Parameter subParam : ((CollectionParameter) p).getParameters()) {
                updateParameterAccess(t, subParam);
            }
        }

        if (type == DataType.FILE_T || type == DataType.OBJECT_T || type == DataType.PSCO_T || type == DataType.STREAM_T
            || type == DataType.EXTERNAL_STREAM_T || type == DataType.EXTERNAL_PSCO_T
            || type == DataType.BINDING_OBJECT_T || type == DataType.COLLECTION_T) {

            DependencyParameter dPar = (DependencyParameter) p;
            DataAccessId dAccId = dPar.getDataAccessId();
            if (DEBUG) {
                LOGGER.debug("Treating that data " + dAccId + " has been accessed at " + dPar.getDataTarget());
            }

            boolean canceledByException = false;
            if (t.hasTaskGroups()) {
                for (TaskGroup tg : t.getTaskGroupList()) {
                    if (tg.hasException() && t.getStatus() == TaskState.CANCELED) {
                        canceledByException = true;
                    }
                }
            }

            if (t.getOnFailure() == OnFailure.CANCEL_SUCCESSORS
                && (t.getStatus() == TaskState.FAILED || t.getStatus() == TaskState.CANCELED) || canceledByException) {
                this.dip.dataAccessHasBeenCanceled(dAccId);
            } else {
                this.dip.dataHasBeenAccessed(dAccId);
            }
        }
    }

    /**
     * Registers the output values of the task {@code currentTask}.
     *
     * @param currentTask Task.
     * @param dp Dependency Parameter.
     */
    private void registerOutputValues(AbstractTask currentTask, DependencyParameter dp) {
        int currentTaskId = currentTask.getId();
        int dataId = dp.getDataAccessId().getDataId();
        Long appId = currentTask.getAppId();

        if (DEBUG) {
            LOGGER.debug("Checking WRITE dependency for datum " + dataId + " and task " + currentTaskId);
        }

        // Update global last writers
        switch (dp.getType()) {
            case STREAM_T:
            case EXTERNAL_STREAM_T:
                WritersInfo wi = this.writers.get(dataId);
                if (wi != null) {
                    wi.addStreamWriter(currentTask);
                } else {
                    wi = new WritersInfo(dp.getType(), Arrays.asList(currentTask));
                }
                this.writers.put(dataId, wi);

                if (IS_DRAW_GRAPH) {
                    drawStreamEdge(currentTask, dp, true);
                }
                break;
            default:
                // Substitute the current entry by the new access
                WritersInfo newWi = new WritersInfo(dp.getType(), currentTask);
                LOGGER.info("Setting writer for data " + dataId);
                this.writers.put(dataId, newWi);
                break;
        }

        // Update file and PSCO lists
        switch (dp.getType()) {
            case FILE_T:
                Set<Integer> fileIdsWritten = this.appIdToWrittenFiles.get(appId);
                if (fileIdsWritten == null) {
                    fileIdsWritten = new TreeSet<>();
                    this.appIdToWrittenFiles.put(appId, fileIdsWritten);
                }
                fileIdsWritten.add(dataId);
                break;
            case PSCO_T:
                Set<Integer> pscoIdsWritten = this.appIdToSCOWrittenIds.get(appId);
                if (pscoIdsWritten == null) {
                    pscoIdsWritten = new TreeSet<>();
                    this.appIdToSCOWrittenIds.put(appId, pscoIdsWritten);
                }
                pscoIdsWritten.add(dataId);
                break;
            default:
                // Nothing to do with basic types
                // Objects are not checked, their version will be only get if the main accesses them
                break;
        }

        if (DEBUG) {
            LOGGER.debug("New writer for datum " + dataId + " is task " + currentTaskId);
        }
    }

    private void checkResultFileTransfer(Task t) {
        LinkedList<DataInstanceId> fileIds = new LinkedList<>();
        for (Parameter p : t.getTaskDescription().getParameters()) {
            switch (p.getType()) {
                case FILE_T:
                    FileParameter fp = (FileParameter) p;
                    switch (fp.getDirection()) {
                        case IN:
                        case CONCURRENT:
                            break;
                        case COMMUTATIVE:
                        case INOUT:
                            DataInstanceId dId = ((RWAccessId) fp.getDataAccessId()).getWrittenDataInstance();
                            WritersInfo wi = this.writers.get(dId.getDataId());
                            if (wi != null) {
                                switch (wi.getDataType()) {
                                    case STREAM_T:
                                    case EXTERNAL_STREAM_T:
                                        // Streams have no result files regarding their direction
                                        break;
                                    default:
                                        AbstractTask lastWriter = wi.getDataWriter();
                                        if (lastWriter != null && lastWriter == t) {
                                            fileIds.add(dId);
                                        }
                                        break;
                                }
                            }
                            break;
                        case OUT:
                            dId = ((WAccessId) fp.getDataAccessId()).getWrittenDataInstance();
                            wi = this.writers.get(dId.getDataId());
                            if (wi != null) {
                                switch (wi.getDataType()) {
                                    case STREAM_T:
                                        // Streams have no result files regarding their direction
                                        break;
                                    default:
                                        AbstractTask lastWriter = wi.getDataWriter();
                                        if (lastWriter != null && lastWriter == t) {
                                            fileIds.add(dId);
                                        }
                                        break;
                                }
                            }
                            break;
                    }
                    break;
                default:
                    break;
            }
        }

        // Order the transfer of the result files
        final int numFT = fileIds.size();
        if (numFT > 0) {
            if (DEBUG) {
                LOGGER.debug("Ordering transfers for result files of task " + t.getId());
            }
            for (DataInstanceId fileId : fileIds) {
                int id = fileId.getDataId();
                if (DEBUG) {
                    LOGGER.debug("- Requesting result file " + id + " because of task " + t.getId());
                }
                this.dip.blockDataAndGetResultFile(id, new ResultListener(new Semaphore(0)));
                this.dip.unblockDataId(id);
            }
        }
    }

    /*
     **************************************************************************************************************
     * GRAPH WRAPPERS
     **************************************************************************************************************/

    /**
     * Adds edges to graph.
     *
     * @param currentTask New task.
     * @param dp Dependency parameter causing the dependency.
     * @param lastWriter Last writer task.
     */
    private void drawEdges(Task currentTask, DependencyParameter dp, AbstractTask lastWriter) {
        // Retrieve common information
        int dataId = dp.getDataAccessId().getDataId();
        Direction d = dp.getDataAccessId().getDirection();
        int dataVersion;
        switch (d) {
            case C:
            case R:
                dataVersion = ((RAccessId) dp.getDataAccessId()).getRVersionId();
                break;
            case W:
                dataVersion = ((WAccessId) dp.getDataAccessId()).getWVersionId();
                break;
            default:
                dataVersion = ((RWAccessId) dp.getDataAccessId()).getRVersionId();
                break;
        }

        // Add edges on graph depending on the dependency type
        switch (dp.getType()) {
            case STREAM_T:
            case EXTERNAL_STREAM_T:
                drawStreamEdge(currentTask, dp, !d.equals(Direction.R));
                break;
            default:
                if (lastWriter != null && lastWriter != currentTask) {
                    if (lastWriter instanceof Task) {
                        if (lastWriter.getSuccessors().contains(currentTask.getCommutativeGroup(dataId))) {
                            addEdgeFromCommutativeToTask(currentTask, dataId, dataVersion,
                                ((CommutativeGroupTask) lastWriter), false);
                        } else {
                            addDataEdgeFromTaskToTask((Task) lastWriter, currentTask, dataId, dataVersion);
                        }
                    } else if (!(lastWriter instanceof Task && !currentTask.hasCommutativeParams())) {
                        addEdgeFromCommutativeToTask(currentTask, dataId, dataVersion,
                            ((CommutativeGroupTask) lastWriter), true);
                    }
                } else {
                    addDataEdgeFromMainToTask(currentTask, dataId, dataVersion);
                }
                break;
        }
    }

    /**
     * Adds the stream node and edge to the graph.
     * 
     * @param currentTask Writer or reader task.
     * @param dp Stream parameter.
     * @param isWrite Whether the task is reading or writing the stream parameter.
     */
    private void drawStreamEdge(AbstractTask currentTask, DependencyParameter dp, boolean isWrite) {
        String stream = "Stream" + dp.getDataAccessId().getDataId();

        // Add stream node even if it exists
        addStreamToGraph(stream);

        // Add dependency
        addStreamEdge(currentTask, stream, isWrite);
    }

    /**
     * We have detected a new task, register it into the graph. STEPS: Only adds the node.
     *
     * @param task New task.
     */
    private void addNewTask(Task task) {
        // Set the syncId of the task
        task.setSynchronizationId(this.synchronizationId);
        // Update current sync status
        this.taskDetectedAfterSync = true;

        // Add task to graph
        if (task.hasCommutativeParams()) {
            // In case task has commutative params, it will be added to graph with the group
        } else {
            // Add node to graph
            addTaskToGraph(task);
        }
    }

    /**
     * Adds the task to the graph to print.
     * 
     * @param task Task to print.
     */
    private void addTaskToGraph(Task task) {
        // Add task to graph
        this.gm.addTaskToGraph(task);
    }

    /**
     * Adds a stream node to the graph to print.
     * 
     * @param stream Stream name to print.
     */
    private void addStreamToGraph(String stream) {
        // Add stream to graph
        this.gm.addStreamToGraph(stream);
    }

    /**
     * Checks if the previous group was printed on the graph.
     *
     * @param dataId Data Id.
     * @param currentTask Task to check.
     */
    private void checkIfPreviousGroupInGraph(int dataId, Task currentTask) {
        WritersInfo wi = this.writers.get(dataId);
        if (wi != null) {
            AbstractTask lastWriter = wi.getDataWriter();

            if (lastWriter instanceof CommutativeGroupTask && !((CommutativeGroupTask) lastWriter).getGraphDrawn()) {
                CommutativeIdentifier comId = ((CommutativeGroupTask) lastWriter).getCommutativeIdentifier();

                // Adds the group to the graph and removes task from pendingToDraw
                addCommutativeGroupTaskToGraph(comId.toString());
                ((CommutativeGroupTask) lastWriter).setGraphDrawn();
                this.pendingToDrawCommutative
                    .remove(((CommutativeGroupTask) lastWriter).getCommutativeIdentifier().toString());
            }
        }
    }

    /**
     * Puts a new commutative group to the graph.
     * 
     * @param identifier Commutative group Id.
     */
    private void addCommutativeGroupTaskToGraph(String identifier) {
        LOGGER.debug("Adding commutative group to graph");
        this.gm.addCommutativeGroupToGraph(identifier);
        for (Task t : this.pendingToDrawCommutative.get(identifier)) {
            addTaskToGraph(t);
        }
        this.gm.closeGroupInGraph();
    }

    /**
     * We will execute a task whose data is produced by another task. STEPS: Add an edge from the previous task or the
     * last synchronization point to the new task.
     *
     * @param source Source task.
     * @param dest Destination task.
     * @param dataId Data causing the dependency.
     * @param dataVersion Data version.
     */
    private void addDataEdgeFromTaskToTask(Task source, Task dest, int dataId, int dataVersion) {
        if (source.getSynchronizationId() == dest.getSynchronizationId()) {
            String src = String.valueOf(source.getId());
            String dst = String.valueOf(dest.getId());
            String dep = String.valueOf(dataId) + "v" + String.valueOf(dataVersion);
            this.gm.addEdgeToGraph(src, dst, EdgeType.DATA_DEPENDENCY, dep);
        } else {
            String src = "Synchro" + dest.getSynchronizationId();
            String dst = String.valueOf(dest.getId());
            String dep = String.valueOf(dataId) + "v" + String.valueOf(dataVersion);
            this.gm.addEdgeToGraph(src, dst, EdgeType.DATA_DEPENDENCY, dep);
        }
    }

    /**
     * We will execute a task with no predecessors, data must be retrieved from the last synchronization point. STEPS:
     * Add edge from sync to task
     *
     * @param dest Destination task.
     * @param dataId Data causing the dependency.
     * @param dataVersion Data version.
     */
    private void addDataEdgeFromMainToTask(Task dest, int dataId, int dataVersion) {
        String src = "Synchro" + dest.getSynchronizationId();
        String dst = String.valueOf(dest.getId());
        String dep = String.valueOf(dataId) + "v" + String.valueOf(dataVersion);
        this.gm.addEdgeToGraph(src, dst, EdgeType.DATA_DEPENDENCY, dep);
    }

    /**
     * We will execute a task with no predecessors. Add edge from sync to task.
     *
     * @param dest Destination task.
     * @param dataId Data causing the dependency.
     * @param dataVersion Data version.
     */
    private void addEdgeFromMainToTask(Task dest) {
        String src = "Synchro" + dest.getSynchronizationId();
        String dst = String.valueOf(dest.getId());
        String dep = "";
        this.gm.addEdgeToGraph(src, dst, EdgeType.DATA_DEPENDENCY, dep);
    }

    /**
     * We have accessed to data produced by a task from the main code STEPS: Adds a new synchronization point if any
     * task has been created Adds a dependency from task to synchronization.
     *
     * @param task Task that generated the value.
     * @param edgeType Type of edge for the DOT representation.
     * @param dataId Data causing the dependency.
     */
    private void addEdgeFromTaskToMain(AbstractTask task, EdgeType edgeType, int dataId, int dataVersion) {
        // Add Sync if any task has been created
        if (this.taskDetectedAfterSync) {
            this.taskDetectedAfterSync = false;

            int oldSyncId = this.synchronizationId;
            this.synchronizationId++;

            this.gm.addSynchroToGraph(this.synchronizationId);

            String oldSync = "Synchro" + oldSyncId;
            String currentSync = "Synchro" + this.synchronizationId;
            this.gm.addEdgeToGraph(oldSync, currentSync, edgeType, "");
        }

        // Add edge from task to sync
        String dest = "Synchro" + this.synchronizationId;
        if (task instanceof CommutativeGroupTask && !((CommutativeGroupTask) task).getCommutativeTasks().isEmpty()) {
            // Add edge from commutative group to synch
            CommutativeGroupTask commGroupTask = (CommutativeGroupTask) task;
            String src = String.valueOf(commGroupTask.getCommutativeTasks().get(0).getId());
            String groupId = commGroupTask.getCommutativeIdentifier().toString();
            this.gm.addEdgeToGraphFromGroup(src, dest, String.valueOf(dataId) + "v" + String.valueOf(dataVersion),
                groupId, "clusterCommutative", edgeType);
        } else {
            // Add edge from task to sync
            String src = String.valueOf(task.getId());
            this.gm.addEdgeToGraph(src, dest, edgeType, String.valueOf(dataId) + "v" + String.valueOf(dataVersion));
        }
    }

    /**
     * Adds a stream edge between a stream node and a task.
     * 
     * @param task Task to write.
     * @param stream Stream to write.
     * @param isWrite Whether the task is writing or reading the stream.L
     */
    private void addStreamEdge(AbstractTask task, String stream, boolean isWrite) {
        // Add dependency
        String taskId = String.valueOf(task.getId());
        if (isWrite) {
            this.gm.addEdgeToGraph(taskId, stream, EdgeType.STREAM_DEPENDENCY, "");
        } else {
            this.gm.addEdgeToGraph(stream, taskId, EdgeType.STREAM_DEPENDENCY, "");
        }
    }

    /**
     * Addition of an edge from the commutative group to a task.
     *
     * @param dest Destination task.
     * @param dataId Id of the data causing the dependency.
     * @param dataVersion Version of the data causing the dependency.
     * @param cgt Commutative task group.
     * @param comToTask Whether the edge should be printed as a group to task or viceversa.
     */
    private void addEdgeFromCommutativeToTask(Task dest, int dataId, int dataVersion, CommutativeGroupTask cgt,
        boolean comToTask) {
        String src = String.valueOf(cgt.getCommutativeTasks().get(0).getId());
        String dst = String.valueOf(dest.getId());
        String dep = String.valueOf(dataId) + "v" + String.valueOf(dataVersion);
        String comId = cgt.getCommutativeIdentifier().toString();
        if (comToTask) {
            this.gm.addEdgeToGraphFromGroup(src, dst, dep, comId, "clusterCommutative", EdgeType.DATA_DEPENDENCY);
        } else {
            this.gm.addEdgeToGraphFromGroup(dst, src, dep, comId, "clusterCommutative", EdgeType.DATA_DEPENDENCY);
        }
    }

    /**
     * We have explicitly called the barrier API. STEPS: Add a new synchronization node. Add an edge from last
     * synchronization point to barrier. Add edges from writer tasks to barrier.
     */
    private void addNewBarrier() {
        // Add barrier node
        int oldSync = this.synchronizationId;
        String oldSyncStr = "Synchro" + oldSync;

        // Add barrier node and edge from last sync
        this.synchronizationId++;
        String newSyncStr = "Synchro" + this.synchronizationId;
        this.gm.addBarrierToGraph(this.synchronizationId);
        this.gm.addEdgeToGraph(oldSyncStr, newSyncStr, EdgeType.USER_DEPENDENCY, "");

        // Reset task detection
        this.taskDetectedAfterSync = false;

        // Add edges from writers to barrier
        Set<AbstractTask> uniqueWriters = new HashSet<>();
        for (WritersInfo wi : this.writers.values()) {
            if (wi != null) {
                // Add data writers
                AbstractTask dataWriter = wi.getDataWriter();
                if (dataWriter != null) {
                    uniqueWriters.add(dataWriter);
                }
                // Add stream writers
                uniqueWriters.addAll(wi.getStreamWriters());
            }
        }
        for (AbstractTask writer : uniqueWriters) {
            if (writer != null && writer.getSynchronizationId() == oldSync) {
                String taskId = String.valueOf(writer.getId());
                this.gm.addEdgeToGraph(taskId, newSyncStr, EdgeType.USER_DEPENDENCY, "");
            }
        }
    }

    /**
     * We have explicitly called the barrier group API call. STEPS: Add a new synchronization node. Add an edge from
     * last synchronization point to barrier. Add edges from group tasks to barrier.
     *
     * @param groupName Name of the group.
     */
    private void addNewGroupBarrier(TaskGroup tg) {
        // Add barrier node
        int oldSync = this.synchronizationId;
        String oldSyncStr = "Synchro" + oldSync;

        // Add barrier node and edge from last sync
        this.synchronizationId++;
        String newSyncStr = "Synchro" + this.synchronizationId;
        this.gm.addBarrierToGraph(this.synchronizationId);
        this.gm.addEdgeToGraph(oldSyncStr, newSyncStr, EdgeType.USER_DEPENDENCY, "");

        // Reset task detection
        this.taskDetectedAfterSync = false;

        String src = String.valueOf(tg.getLastTaskId());
        tg.setBarrierDrawn();
        if (!tg.hasPendingTasks() && tg.isClosed() && tg.hasBarrier()) {
            this.taskGroups.get(tg.getAppId()).remove(tg.getName());
        }
        this.gm.addEdgeToGraphFromGroup(src, newSyncStr, "", tg.getName(), "clusterTasks", EdgeType.USER_DEPENDENCY);
    }


    private static class WritersInfo {

        private final DataType dataType;
        private AbstractTask dataWriter;
        private final List<AbstractTask> streamWriters;


        public WritersInfo(DataType dataType, AbstractTask dataWriter) {
            this.dataType = dataType;
            this.dataWriter = dataWriter;
            this.streamWriters = new ArrayList<>();
        }

        public void setDataWriter(AbstractTask dataWriter) {
            this.dataWriter = dataWriter;
        }

        public WritersInfo(DataType dataType, List<AbstractTask> streamWriters) {
            this.dataType = dataType;
            this.dataWriter = null;
            this.streamWriters = new ArrayList<>();
            if (streamWriters != null) {
                this.streamWriters.addAll(streamWriters);
            }
        }

        public DataType getDataType() {
            return this.dataType;
        }

        public AbstractTask getDataWriter() {
            return this.dataWriter;
        }

        public List<AbstractTask> getStreamWriters() {
            return this.streamWriters;
        }

        public void addStreamWriter(AbstractTask writerTask) {
            if (writerTask != null) {
                this.streamWriters.add(writerTask);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("WI [ ");
            sb.append("dataType = ").append(this.dataType).append(", ");
            sb.append("dataWriter = ").append(this.dataWriter != null ? this.dataWriter.getId() : "null").append(", ");
            sb.append("streamWriters = [");
            for (AbstractTask t : this.streamWriters) {
                sb.append(t.getId()).append(" ");
            }
            sb.append("]");
            sb.append("]");

            return sb.toString();
        }

    }

}
