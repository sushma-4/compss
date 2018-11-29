/*         
 *  Copyright 2002-2018 Barcelona Supercomputing Center (www.bsc.es)
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
package es.bsc.compss.invokers.commands.piped;

import es.bsc.compss.invokers.commands.external.ErrorTaskExternalCommand;
import es.bsc.compss.invokers.external.piped.PipeCommand;
import es.bsc.compss.invokers.types.ExternalTaskStatus;


public class ErrorTaskPipeCommand extends ErrorTaskExternalCommand implements PipeCommand {

    public ErrorTaskPipeCommand(String[] result) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public ExternalTaskStatus getTaskStatus() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}