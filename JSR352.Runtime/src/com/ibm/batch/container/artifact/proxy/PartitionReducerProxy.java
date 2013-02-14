/*
 * Copyright 2012 International Business Machines Corp.
 * 
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. Licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.ibm.batch.container.artifact.proxy;

import javax.batch.api.PartitionReducer;

import com.ibm.batch.container.exception.BatchContainerRuntimeException;

public class PartitionReducerProxy extends AbstractProxy<PartitionReducer> implements PartitionReducer {

    PartitionReducerProxy(PartitionReducer delegate) { 
        super(delegate);

    }

    @Override
    public void afterPartitionedStepCompletion(PartitionStatus status) {
        
        try {
            this.delegate.afterPartitionedStepCompletion(status);
        } catch (Exception e) {
            throw new BatchContainerRuntimeException(e);
        }
    }

    @Override
    public void beforePartitionedStepCompletion() {
        
        try {
            this.delegate.beforePartitionedStepCompletion();
        } catch (Exception e) {
            throw new BatchContainerRuntimeException(e);
        }
    }

    @Override
    public void beginPartitionedStep() {

        try {
            this.delegate.beginPartitionedStep();
        } catch (Exception e) {
            throw new BatchContainerRuntimeException(e);
        }
    }

    @Override
    public void rollbackPartitionedStep() {
        
        try {
            this.delegate.rollbackPartitionedStep();
        } catch (Exception e) {
            throw new BatchContainerRuntimeException(e);
        }
    }

}


