package es.bsc.compss.nio;

import es.bsc.compss.COMPSsConstants;
import es.bsc.compss.types.data.location.DataLocation.Protocol;
import es.bsc.compss.util.Tracer;
import es.bsc.compss.util.StreamGobbler;
import es.bsc.cepbatools.extrae.Wrapper;

import java.io.File;
import java.io.IOException;

import static java.lang.Math.abs;


public class NIOTracer extends Tracer {

    private static String scriptDir = "";
    private static String workingDir = "";
    private static String nodeName = "master"; // while no worker sets the Tracer info we assume we are on master
    private static String hostID = "0"; // while no worker sets the Tracer info we assume we are on master
    private static final int ID = 121; // Random value

    public static final String TRANSFER_END = "0";


    public static void init(int level) {
        if (debug) {
            logger.debug("Initializing NIO tracing");
        }
        tracing_level = level;
    }

    public static void startTracing(String workerName, String workerUser, String workerHost, Integer numThreads) {
        if (numThreads <= 0) {
            if (debug) {
                logger.debug("Resource " + workerName + " has 0 slots, it won't appear in the trace");
            }
            return;
        }

        if (debug) {
            logger.debug("NIO uri File: " + Protocol.ANY_URI.getSchema() + File.separator + System.getProperty(COMPSsConstants.APP_LOG_DIR) + traceOutRelativePath);
            logger.debug(Protocol.ANY_URI.getSchema() + File.separator + System.getProperty(COMPSsConstants.APP_LOG_DIR) + traceOutRelativePath);
        }
    }

    public static void setWorkerInfo(String scriptDir, String nodeName, String workingDir, int hostID) {
        NIOTracer.scriptDir = scriptDir;
        NIOTracer.workingDir = workingDir;
        NIOTracer.nodeName = nodeName;
        NIOTracer.hostID = String.valueOf(hostID);

        synchronized (Tracer.class) {
            Wrapper.SetTaskID(hostID);
            Wrapper.SetNumTasks(hostID + 1);
        }

        if (debug) {
            logger.debug("Tracer worker for host " + hostID + " and: " + NIOTracer.scriptDir + ", " 
                            + NIOTracer.workingDir + ", " + NIOTracer.nodeName);
        }
    }

    public static String getHostID(){
        return hostID;
    }

    public static void emitDataTransferEvent(String data) {
        boolean dataTransfer = !(data.startsWith("worker")) && !(data.startsWith("tracing"));

        int transferID = (data.equals(TRANSFER_END)) ? 0 : abs(data.hashCode());

        if (dataTransfer) {
            emitEvent(transferID, getDataTransfersType());
        }

        if (debug) {
            logger.debug((dataTransfer ? "E" : "Not E") + "mitting synchronized data transfer event [name, id] = [" + data + " , "
                    + transferID + "]");
        }
    }

    public static void emitCommEvent(boolean send, int partnerID, int tag) {
        emitCommEvent(send, partnerID, tag, 0);
    }

    public static void emitCommEvent(boolean send, int partnerID, int tag, long size) {
        synchronized (Tracer.class) {
            Wrapper.Comm(send, tag, (int) size, partnerID, ID);
        }

        if (debug) {
            logger.debug("Emitting communication event [" + (send ? "SEND" : "REC") + "] " + tag + ", " + size + ", " + partnerID + ", "
                    + ID + "]");
        }
    }

    public static void generatePackage() {
        emitEvent(Event.STOP.getId(), Event.STOP.getType());
        if (debug) {
            logger.debug("[NIOTracer] Generating trace package of " + nodeName);
        }
        emitEvent(Tracer.EVENT_END, Tracer.getRuntimeEventsType());

        synchronized (Tracer.class) {
        	if (debug) {
                logger.debug("[NIOTracer] Disabling pthreads.");
            }
            Wrapper.SetOptions(Wrapper.EXTRAE_ENABLE_ALL_OPTIONS & ~Wrapper.EXTRAE_PTHREAD_OPTION);

            if (debug) {
                logger.debug("[NIOTracer] Finishing extrae.");
            }
            // End wrapper
            Wrapper.Fini();
        }
        if (debug) {
            logger.debug("[NIOTracer] Executing command " + scriptDir + TRACE_SCRIPT_PATH + " package " + workingDir + " " + nodeName + " " + hostID);
        }
        // Generate package
        ProcessBuilder pb = new ProcessBuilder(scriptDir + TRACE_SCRIPT_PATH, "package", workingDir, nodeName, hostID);
        pb.environment().remove(LD_PRELOAD);
        Process p = null;
        try {
        	
            p = pb.start();
        } catch (IOException e) {
            logger.error("Error generating " + nodeName + " package", e);
            return;
        }

        // Only capture output/error if debug level (means 2 more threads)
        if (debug) {
            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), System.out, logger);
            StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), System.err, logger);
            outputGobbler.start();
            errorGobbler.start();
        }

        try {
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                logger.error("Error generating " + nodeName + " package, exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            logger.error("Error generating " + nodeName + " package (interruptedException) : " + e.getMessage());
        }
        if (debug) {
            logger.debug("Finish generating");
        }
    }

}
