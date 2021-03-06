#!/usr/bin/python
#
#  Copyright 2002-2019 Barcelona Supercomputing Center (www.bsc.es)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

# -*- coding: utf-8 -*-

"""
PyCOMPSs Binding - Interactive API
==================================
    Provides the current start and stop for the use of PyCOMPSs interactively.
"""

import os
import sys
import logging
from tempfile import mkdtemp
import time

import pycompss.util.context as context
import pycompss.runtime.binding as binding
import pycompss.util.interactive.helpers as interactive_helpers
from pycompss.runtime.binding import get_log_path
from pycompss.runtime.binding import pending_to_synchronize
from pycompss.runtime.commons import RUNNING_IN_SUPERCOMPUTER
from pycompss.util.environment.configuration import prepare_environment
from pycompss.util.environment.configuration import prepare_loglevel_graph_for_monitoring  # noqa: E501
from pycompss.util.environment.configuration import updated_variables_in_sc
from pycompss.util.environment.configuration import prepare_tracing_environment
from pycompss.util.environment.configuration import check_infrastructure_variables  # noqa: E501
from pycompss.util.environment.configuration import create_init_config_file
from pycompss.util.environment.configuration import setup_logger

# Storage imports
from pycompss.util.storages.persistent import init_storage
from pycompss.util.storages.persistent import stop_storage

# Streaming imports
from pycompss.streams.environment import init_streaming
from pycompss.streams.environment import stop_streaming


# GLOBAL VARIABLES
APP_PATH = 'InteractiveMode'
# Warning! The name should start with 'InteractiveMode' due to @task checks
# it explicitly. If changed, it is necessary to update the task decorator.
PERSISTENT_STORAGE = False
STREAMING = False
LOG_PATH = '/tmp/'
GRAPHING = False


def start(log_level='off',
          debug=False,
          o_c=False,
          graph=False,
          trace=False,
          monitor=None,
          project_xml=None,
          resources_xml=None,
          summary=False,
          task_execution='compss',
          storage_impl=None,
          storage_conf=None,
          streaming_backend=None,
          streaming_master_name=None,
          streaming_master_port=None,
          task_count=50,
          app_name='Interactive',
          uuid=None,
          base_log_dir=None,
          specific_log_dir=None,
          extrae_cfg=None,
          comm='NIO',
          conn='es.bsc.compss.connectors.DefaultSSHConnector',
          master_name='',
          master_port='',
          scheduler='es.bsc.compss.scheduler.' +
                    'loadbalancing.LoadBalancingScheduler',
          jvm_workers='-Xms1024m,-Xmx1024m,-Xmn400m',
          cpu_affinity='automatic',
          gpu_affinity='automatic',
          fpga_affinity='automatic',
          fpga_reprogram='',
          profile_input='',
          profile_output='',
          scheduler_config='',
          external_adaptation=False,
          propagate_virtual_environment=True,
          mpi_worker=False,
          verbose=False
          ):
    """
    Start the runtime in interactive mode.

    :param log_level: Logging level [ 'off' | 'info' | 'debug' ]
                      (default: 'off')
    :param debug: Debug mode [ True | False ]
                  (default: False) (overrides log-level)
    :param o_c: Objects to string conversion [ True | False ]
                (default: False)
    :param graph: Generate graph [ True | False ]
                  (default: False)
    :param trace: Generate trace
                  [ True | False | 'scorep' | 'arm-map' | 'arm-ddt' ]
                  (default: False)
    :param monitor: Monitor refresh rate
                    (default: None)
    :param project_xml: Project xml file path
                        (default: None)
    :param resources_xml: Resources xml file path
                          (default: None)
    :param summary: Execution summary [ True | False ]
                    (default: False)
    :param task_execution: Task execution
                           (default: 'compss')
    :param storage_impl: Storage implementation path
                         (default: None)
    :param storage_conf: Storage configuration file path
                         (default: None)
    :param streaming_backend: Streaming backend
                              (default: None)
    :param streaming_master_name: Streaming master name
                                  (default: None)
    :param streaming_master_port: Streaming master port
                                  (default: None)
    :param task_count: Task count
                       (default: 50)
    :param app_name: Application name
                     default: Interactive_date)
    :param uuid: UUId
                 (default: None)
    :param base_log_dir: Base logging directory
                         (default: None)
    :param specific_log_dir: Specific logging directory
                             (default: None)
    :param extrae_cfg: Extrae configuration file path
                       (default: None)
    :param comm: Communication library
                 (default: NIO)
    :param conn: Connector
                 (default: DefaultSSHConnector)
    :param master_name: Master Name
                        (default: '')
    :param master_port: Master port
                        (default: '')
    :param scheduler: Scheduler
                      (default: es.bsc.compss.scheduler.
                      loadbalancing.LoadBalancingScheduler)
    :param jvm_workers: Java VM parameters
                        (default: '-Xms1024m,-Xmx1024m,-Xmn400m')
    :param cpu_affinity: CPU Core affinity
                         (default: 'automatic')
    :param gpu_affinity: GPU affinity
                         (default: 'automatic')
    :param fpga_affinity: FPGA affinity
                          (default: 'automatic')
    :param fpga_reprogram: FPGA repogram command
                           (default: '')
    :param profile_input: Input profile
                          (default: '')
    :param profile_output: Output profile
                           (default: '')
    :param scheduler_config: Scheduler configuration
                             (default: '')
    :param external_adaptation: External adaptation [ True | False ]
                                (default: False)
    :param propagate_virtual_environment: Propagate virtual environment
                                          [ True | False ]
                                          (default: False)
    :param mpi_worker: Use the MPI worker [ True | False ]
                       (default: False)
    :param verbose: Verbose mode [ True | False ]
                    (default: False)
    :return: None
    """

    # Export global variables
    global GRAPHING
    GRAPHING = graph
    __export_globals__()

    interactive_helpers.DEBUG = debug

    __show_flower__()

    # Let the Python binding know we are at master
    context.set_pycompss_context(context.MASTER)
    # Then we can import the appropriate start and stop functions from the API
    from pycompss.api.api import compss_start

    ##############################################################
    # INITIALIZATION
    ##############################################################

    # TODO: Check that input values are valid

    # Initial dictionary with the user defined parameters
    all_vars = {'log_level': log_level,
                'debug': debug,
                'o_c': o_c,
                'graph': graph,
                'trace': trace,
                'monitor': monitor,
                'project_xml': project_xml,
                'resources_xml': resources_xml,
                'summary': summary,
                'task_execution': task_execution,
                'storage_impl': storage_impl,
                'storage_conf': storage_conf,
                'streaming_backend': streaming_backend,
                'streaming_master_name': streaming_master_name,
                'streaming_master_port': streaming_master_port,
                'task_count': task_count,
                'app_name': app_name,
                'uuid': uuid,
                'base_log_dir': base_log_dir,
                'specific_log_dir': specific_log_dir,
                'extrae_cfg': extrae_cfg,
                'comm': comm,
                'conn': conn,
                'master_name': master_name,
                'master_port': master_port,
                'scheduler': scheduler,
                'jvm_workers': jvm_workers,
                'cpu_affinity': cpu_affinity,
                'gpu_affinity': gpu_affinity,
                'fpga_affinity': fpga_affinity,
                'fpga_reprogram': fpga_reprogram,
                'profile_input': profile_input,
                'profile_output': profile_output,
                'scheduler_config': scheduler_config,
                'external_adaptation': external_adaptation,
                'propagate_virtual_environment': propagate_virtual_environment,
                'mpi_worker': mpi_worker}

    # Prepare the environment
    env_vars = prepare_environment(True, o_c, storage_impl,
                                   None, debug, trace, mpi_worker)
    all_vars.update(env_vars)

    # Update the log level and graph values if monitoring is enabled
    monitoring_vars = prepare_loglevel_graph_for_monitoring(monitor,
                                                            graph,
                                                            debug,
                                                            log_level)
    all_vars.update(monitoring_vars)

    # Check if running in supercomputer and update the variables accordingly
    # with the defined in the launcher and exported in environment variables.
    if RUNNING_IN_SUPERCOMPUTER:
        updated_vars = updated_variables_in_sc()
        if verbose:
            print("- Overridden project xml with: " +
                  updated_vars['project_xml'])
            print("- Overridden resources xml with: " +
                  updated_vars['resources_xml'])
            print("- Overridden master name with: " +
                  updated_vars['master_name'])
            print("- Overridden master port with: " +
                  updated_vars['master_port'])
            print("- Overridden uuid with: " +
                  updated_vars['uuid'])
            print("- Overridden base log dir with: " +
                  updated_vars['base_log_dir'])
            print("- Overridden specific log dir with: " +
                  updated_vars['specific_log_dir'])
            print("- Overridden storage conf with: " +
                  updated_vars['storage_conf'])
            print("- Overridden log level with: " +
                  str(updated_vars['log_level']))
            print("- Overridden debug with: " +
                  str(updated_vars['debug']))
            print("- Overridden trace with: " +
                  str(updated_vars['trace']))
        all_vars.update(updated_vars)

    # Update the tracing environment if set and set the appropriate trace
    # integer value
    tracing_vars = prepare_tracing_environment(all_vars['trace'],
                                               all_vars['extrae_lib'],
                                               all_vars['ld_library_path'])
    all_vars['trace'], all_vars['ld_library_path'] = tracing_vars

    # Update the infrastructure variables if necessary
    inf_vars = check_infrastructure_variables(all_vars['project_xml'],
                                              all_vars['resources_xml'],
                                              all_vars['compss_home'],
                                              all_vars['app_name'],
                                              all_vars['file_name'],
                                              all_vars['external_adaptation'])
    all_vars.update(inf_vars)

    # With all this information, create the configuration file for the
    # runtime start
    create_init_config_file(**all_vars)

    ##############################################################
    # RUNTIME START
    ##############################################################

    print("* - Starting COMPSs runtime...                       *")
    sys.stdout.flush()  # Force flush
    compss_start()

    global LOG_PATH
    LOG_PATH = get_log_path()
    binding.temp_dir = mkdtemp(prefix='pycompss', dir=LOG_PATH + '/tmpFiles/')
    print("* - Log path : " + LOG_PATH)

    major_version = all_vars['major_version']
    compss_home = all_vars['compss_home']
    logger = setup_logger(debug, log_level, major_version,
                          compss_home, LOG_PATH)

    __print_setup__(verbose, all_vars)

    logger.debug("--- START ---")
    logger.debug("PyCOMPSs Log path: %s" % LOG_PATH)

    logger.debug("Starting storage")
    global PERSISTENT_STORAGE
    PERSISTENT_STORAGE = init_storage(all_vars['storage_conf'], logger)

    logger.debug("Starting streaming")
    global STREAMING
    STREAMING = init_streaming(all_vars['streaming_backend'],
                               all_vars['streaming_master_name'],
                               all_vars['streaming_master_port'],
                               logger)

    # MAIN EXECUTION
    # let the user write an interactive application
    print("* - PyCOMPSs Runtime started... Have fun!            *")
    print("******************************************************")


def __show_flower__():
    """
    Shows the flower and version through stdout.

    :return: None
    """
    print("******************************************************")  # noqa
    print("*************** PyCOMPSs Interactive *****************")  # noqa
    print("******************************************************")  # noqa
    print("*          .-~~-.--.           _____          __     *")  # noqa
    print("*         :         )         |____ \        / /     *")  # noqa
    print("*   .~ ~ -.\       /.- ~~ .     ___) |      / /__    *")  # noqa
    print("*   >       `.   .'       <    / ___/      /  _  \   *")  # noqa
    print("*  (         .- -.         )  | |___   _  |  |_|  )  *")  # noqa
    print("*   `- -.-~  `- -'  ~-.- -'   |_____| |_|  \_____/   *")  # noqa
    print("*     (        :        )           _ _ .-:          *")  # noqa
    print("*      ~--.    :    .--~        .-~  .-~  }          *")  # noqa
    print("*          ~-.-^-.-~ \_      .~  .-~   .~            *")  # noqa
    print("*                   \ \ '     \ '_ _ -~              *")  # noqa
    print("*                    \`.\`.    //                    *")  # noqa
    print("*           . - ~ ~-.__\`.\`-.//                     *")  # noqa
    print("*       .-~   . - ~  }~ ~ ~-.~-.                     *")  # noqa
    print("*     .' .-~      .-~       :/~-.~-./:               *")  # noqa
    print("*    /_~_ _ . - ~                 ~-.~-._            *")  # noqa
    print("*                                     ~-.<           *")  # noqa
    print("******************************************************")  # noqa


def __print_setup__(verbose, all_vars):
    """
    Print the setup variables through stdout (only if verbose is True).
    However, it shows them through the logger.

    :param verbose: Verbose mode [True | False]
    :param all_vars: Dictionary containing all variables.
    :return: None
    """
    logger = logging.getLogger(__name__)
    output = ""
    output += "******************************************************\n"
    output += " CONFIGURATION: \n"
    for k, v in sorted(all_vars.items()):
        output += '  - {0:20} : {1} \n'.format(k, v)
    output += "******************************************************"
    if verbose:
        print(output)
    logger.debug(output)


def stop(sync=False):
    """
    Runtime stop.

    :param sync: Scope variables synchronization [ True | False ]
                 (default: False)
    :return: None
    """
    from pycompss.api.api import compss_stop

    print("****************************************************")
    print("*************** STOPPING PyCOMPSs ******************")
    print("****************************************************")

    logger = logging.getLogger(__name__)

    if sync:
        sync_msg = "Synchronizing all future objects left on the user scope."
        print(sync_msg)
        logger.debug(sync_msg)
        from pycompss.api.api import compss_wait_on

        ipython = globals()['__builtins__']['get_ipython']()
        # import pprint
        # pprint.pprint(ipython.__dict__, width=1)
        raw_code = ipython.__dict__['user_ns']
        for k in raw_code:
            obj_k = raw_code[k]
            if not k.startswith('_'):   # not internal objects
                if type(obj_k) == binding.Future:
                    print("Found a future object: %s" % str(k))
                    logger.debug("Found a future object: %s" % (k,))
                    ipython.__dict__['user_ns'][k] = compss_wait_on(obj_k)
                elif obj_k in pending_to_synchronize.values():
                    print("Found an object to synchronize: %s" % str(k))
                    logger.debug("Found an object to synchronize: %s" % (k,))
                    ipython.__dict__['user_ns'][k] = compss_wait_on(obj_k)
                else:
                    pass
    else:
        print("Warning: some of the variables used with PyCOMPSs may")
        print("         have not been brought to the master.")

    if STREAMING:
        logger.debug("Stopping streaming")
        stop_streaming(logger)

    if PERSISTENT_STORAGE:
        logger.debug("Stopping persistent storage")
        stop_storage()

    compss_stop()

    __clean_temp_files__()

    # Let the Python binding know we are not at master anymore
    context.set_pycompss_context(context.OUT_OF_SCOPE)

    print("****************************************************")
    logger.debug("--- END ---")
    # os._exit(00)  # Explicit kernel restart # breaks Jupyter-notebook

    # --- Execution finished ---


def __show_current_graph__(fit=False):
    """
    Show current graph.

    :param fit: Fit to width [ True | False ] (default: False)
    :return: None
    """

    if GRAPHING:
        return __show_graph__(name='current_graph', fit=fit)
    else:
        print('Oops! Graph is not enabled in this execution.')
        print('      Please, enable it by setting the graph flag when' +
              ' starting PyCOMPSs.')


def __show_complete_graph__(fit=False):
    """
    Show complete graph.

    :param fit: Fit to width [ True | False ] (default: False)
    :return: None
    """

    if GRAPHING:
        return __show_graph__(name='complete_graph', fit=fit)
    else:
        print('Oops! Graph is not enabled in this execution.')
        print('      Please, enable it by setting the graph flag when' +
              ' starting PyCOMPSs.')


def __show_graph__(name='complete_graph', fit=False):
    """
    Show graph.

    :param name: Graph to show (default: 'complete_graph')
    :param fit: Fit to width [ True | False ] (default: False)
    :return: None
    """

    try:
        from graphviz import Source
    except ImportError:
        print('Oops! graphviz is not available.')
        raise
    monitor_file = open(LOG_PATH + '/monitor/' + name + '.dot', 'r')
    text = monitor_file.read()
    monitor_file.close()
    if fit:
        try:
            # Convert to png and show full picture
            filename = LOG_PATH + '/monitor/' + name
            extension = 'png'
            import os
            if os.path.exists(filename + '.' + extension):
                os.remove(filename + '.' + extension)
            s = Source(text, filename=filename, format=extension)
            s.render()
            from IPython.display import Image
            image = Image(filename=filename + '.' + extension)
            return image
        except Exception:
            print('Oops! Failed rendering the graph.')
            raise
    else:
        return Source(text)


# ########################################################################### #
# ########################################################################### #
# ########################################################################### #


def __export_globals__():
    """
    Export globals into interactive environment.

    :return: None
    """

    # Super ugly, but I see no other way to define the APP_PATH across the
    # interactive execution without making the user to define it explicitly.
    # It is necessary to define only one APP_PATH because of the two decorators
    # need to access the same information.
    # if the file is created per task, the constraint will not be able to work.
    # Get ipython globals
    ipython = globals()['__builtins__']['get_ipython']()
    # import pprint
    # pprint.pprint(ipython.__dict__, width=1)
    # Extract user globals from ipython
    user_globals = ipython.__dict__['ns_table']['user_global']
    # Inject APP_PATH variable to user globals so that task and constraint
    # decorators can get it.
    temp_app_filename = os.getcwd() + '/' + "InteractiveMode_"
    temp_app_filename += str(time.strftime('%d%m%y_%H%M%S')) + '.py'
    user_globals['APP_PATH'] = temp_app_filename
    global APP_PATH
    APP_PATH = temp_app_filename


def __clean_temp_files__():
    """
    Remove any temporary files that may exist.
    Currently: APP_PATH, which contains the file path where all interactive
               code required by the worker is.

    :return: None
    """

    try:
        if os.path.exists(APP_PATH):
            os.remove(APP_PATH)
        if os.path.exists(APP_PATH + 'c'):
            os.remove(APP_PATH + 'c')
    except OSError:
        print("[ERROR] An error has occurred when cleaning temporary files.")
