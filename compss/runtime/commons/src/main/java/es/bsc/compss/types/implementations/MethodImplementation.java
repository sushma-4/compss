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
package es.bsc.compss.types.implementations;

import es.bsc.compss.types.resources.MethodResourceDescription;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


public class MethodImplementation extends AbstractMethodImplementation implements Externalizable {

    /**
     * Runtime Objects have serialization ID 1L.
     */
    private static final long serialVersionUID = 1L;

    public static final int NUM_PARAMS = 2;

    private String declaringClass;
    // In C implementations could have different method names
    private String alternativeMethod;


    /**
     * Creates a new MethodImplementation for serialization.
     */
    public MethodImplementation() {
        // For externalizable
        super();
    }

    /**
     * Creates a new MethodImplementation instance from the given parameters.
     * 
     * @param methodClass Method class.
     * @param altMethodName Method name.
     * @param coreId Core Id.
     * @param implementationId Implementation id.
     * @param signature Method signature.
     * @param requirements Method annotations.
     */
    public MethodImplementation(String methodClass, String altMethodName, Integer coreId, Integer implementationId,
        String signature, MethodResourceDescription requirements) {

        super(coreId, implementationId, signature, requirements);

        this.declaringClass = methodClass;
        this.alternativeMethod = altMethodName;
    }

    /**
     * Returns the method declaring class.
     * 
     * @return The method declaring class.
     */
    public String getDeclaringClass() {
        return this.declaringClass;
    }

    /**
     * Returns the alternative method name.
     * 
     * @return The alternative method name.
     */
    public String getAlternativeMethodName() {
        return this.alternativeMethod;
    }

    /**
     * Sets a new alternative method name.
     * 
     * @param alternativeMethod The new alternative method name.
     */
    public void setAlternativeMethodName(String alternativeMethod) {
        this.alternativeMethod = alternativeMethod;
    }

    @Override
    public MethodType getMethodType() {
        return MethodType.METHOD;
    }

    @Override
    public String getMethodDefinition() {
        StringBuilder sb = new StringBuilder();
        sb.append("[DECLARING CLASS=").append(this.declaringClass);
        sb.append(", METHOD NAME=").append(this.alternativeMethod);
        sb.append("]");

        return sb.toString();
    }

    @Override
    public String toString() {
        return super.toString() + " Method declared in class " + this.declaringClass + "." + alternativeMethod + ": "
            + this.requirements.toString();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        this.declaringClass = (String) in.readObject();
        this.alternativeMethod = (String) in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeObject(this.declaringClass);
        out.writeObject(this.alternativeMethod);
    }

}
