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
package es.bsc.compss.types.resources.configuration;

import es.bsc.compss.COMPSsConstants;

import java.io.File;


public class MethodConfiguration extends Configuration {

    private static final String DEPLOYMENT_ID = System.getProperty(COMPSsConstants.DEPLOYMENT_ID);

    private String host;
    private String user = "";

    private String installDir = "";
    private String workingDir = "";
    private String sandboxWorkingDir;

    private int totalComputingUnits = 0;
    private int totalGPUComputingUnits = 0;
    private int totalFPGAComputingUnits = 0;
    private int totalOTHERComputingUnits = 0;

    private String appDir = "";
    private String classpath = "";
    private String pythonpath = "";
    private String libraryPath = "";


    public MethodConfiguration(String adaptorName) {
        super(adaptorName);
    }

    /**
     * Method Configuration constructors cloning an existing configuration.
     * @param clone Configuration to clone
     */
    public MethodConfiguration(MethodConfiguration clone) {
        super(clone);
        this.host = clone.host;
        this.user = clone.user;

        this.installDir = clone.installDir;
        this.workingDir = clone.workingDir;
        this.sandboxWorkingDir = clone.sandboxWorkingDir;

        this.totalComputingUnits = clone.totalComputingUnits;
        this.totalGPUComputingUnits = clone.totalGPUComputingUnits;
        this.totalFPGAComputingUnits = clone.totalFPGAComputingUnits;
        this.totalOTHERComputingUnits = clone.totalOTHERComputingUnits;

        this.appDir = clone.appDir;
        this.classpath = clone.classpath;
        this.pythonpath = clone.pythonpath;
        this.libraryPath = clone.libraryPath;
    }

    public MethodConfiguration copy() {
        return new MethodConfiguration(this);
    }

    public String getInstallDir() {
        return installDir;
    }

    /**
     * Set the installation directory.
     * @param installDir Installation directory path
     */
    public void setInstallDir(String installDir) {
        if (installDir == null) {
            this.installDir = "";
        } else if (installDir.isEmpty()) {
            this.installDir = "";
        } else if (!installDir.endsWith(File.separator)) {
            this.installDir = installDir + File.separator;
        } else {
            this.installDir = installDir;
        }
    }

    public String getSandboxWorkingDir() {
        return sandboxWorkingDir;
    }

    public void setSandboxWorkingDir(String sandboxWorkingDir) {
        this.sandboxWorkingDir = sandboxWorkingDir;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    /**
     * Set the working directory.
     * @param workingDir Working directory path
     */
    public void setWorkingDir(String workingDir) {
        if (workingDir == null) {
            // No working dir specified in the project file. Using default tmp
            this.workingDir = File.separator + "tmp" + File.separator;
        } else if (workingDir.isEmpty()) {
            // No working dir specified in the project file. Using default tmp
            this.workingDir = File.separator + "tmp" + File.separator;
        } else if (!workingDir.endsWith(File.separator)) {
            this.workingDir = workingDir + File.separator;
        } else {
            this.workingDir = workingDir;
        }
        String host = this.getHost().replace("/", "_").replace(":", "_"); // Replace nasty characters
        String sandboxWorkingDir = this.workingDir + DEPLOYMENT_ID + File.separator + host + File.separator;
        this.setSandboxWorkingDir(sandboxWorkingDir);
    }

    public int getTotalComputingUnits() {
        return totalComputingUnits;
    }

    /**
     * Set total computing units.
     * @param totalCUs Total computing units
     */
    public void setTotalComputingUnits(int totalCUs) {
        if (totalCUs > 0) {
            this.totalComputingUnits = totalCUs;
        } else {
            this.totalComputingUnits = 0;
        }
    }

    public int getTotalGPUComputingUnits() {
        return totalGPUComputingUnits;
    }

    /**
     * Set total GPU computing units.
     * @param totalGPUs Total GPU computing units
     */
    public void setTotalGPUComputingUnits(int totalGPUs) {
        if (totalGPUs > 0) {
            this.totalGPUComputingUnits = totalGPUs;
        } else {
            this.totalGPUComputingUnits = 0;
        }
    }

    public int getTotalFPGAComputingUnits() {
        return totalFPGAComputingUnits;
    }

    /**
     * Set total FPGA computing units.
     * @param totalFPGAs Total FPGA computing units
     */
    public void setTotalFPGAComputingUnits(int totalFPGAs) {
        if (totalFPGAs > 0) {
            this.totalFPGAComputingUnits = totalFPGAs;
        } else {
            this.totalFPGAComputingUnits = 0;
        }
    }

    public int getTotalOTHERComputingUnits() {
        return totalOTHERComputingUnits;
    }

    /**
     * Set total OTHER computing units.
     * @param totalOTHERs Total OTHER computing units
     */
    public void setTotalOTHERComputingUnits(int totalOTHERs) {
        if (totalOTHERs > 0) {
            this.totalOTHERComputingUnits = totalOTHERs;
        } else {
            this.totalOTHERComputingUnits = 0;
        }
    }

    public String getAppDir() {
        return appDir;
    }

    /**
     * Set the application location directory.
     * @param appDir Application directory path
     */
    public void setAppDir(String appDir) {
        if (appDir == null || appDir.isEmpty()) {
            this.appDir = "";
        } else if (!appDir.endsWith(File.separator)) {
            this.appDir = appDir + File.separator;
        } else {
            this.appDir = appDir;
        }
    }

    public String getClasspath() {
        return classpath;
    }
    
    /**
     * Set the application required classpath.
     * @param classpath Application classpath
     */
    public void setClasspath(String classpath) {
        if (classpath == null) {
            this.classpath = "";
        } else {
            this.classpath = classpath;
        }
    }

    public String getPythonpath() {
        return pythonpath;
    }

    /**
     * Set the application required pythonpath.
     * @param pythonpath Application pythonpath
     */
    public void setPythonpath(String pythonpath) {
        if (pythonpath == null) {
            this.pythonpath = "";
        } else {
            this.pythonpath = pythonpath;
        }
    }

    public String getLibraryPath() {
        return libraryPath;
    }

    /**
     * Set the application required library path.
     * @param libraryPath Application library path
     */
    public void setLibraryPath(String libraryPath) {
        if (libraryPath == null) {
            this.libraryPath = "";
        } else {
            this.libraryPath = libraryPath;
        }
    }

    public String getHost() {
        return host;
    }
    
    /**
     * Set the hostname of the configuration.
     * @param host Hostname
     */
    public void setHost(String host) {
        if (host != null) {
            this.host = host;
        } else {
            this.host = "";
        }
        String newHost = this.getHost().replace("/", "_").replace(":", "_"); // Replace nasty characters
        String sandboxWorkingDir = this.getWorkingDir() + DEPLOYMENT_ID + File.separator + newHost + File.separator;
        this.setSandboxWorkingDir(sandboxWorkingDir);
    }

    public String getUser() {
        return user;
    }

    /** Set the username.
     * @param user username
     */
    public void setUser(String user) {
        if (user != null) {
            this.user = user;
        } else {
            this.user = "";
        }
    }

    // For JClouds connector
    public int getMaxPort() {
        return -1;
    }

    public int getMinPort() {
        return -1;
    }

}
