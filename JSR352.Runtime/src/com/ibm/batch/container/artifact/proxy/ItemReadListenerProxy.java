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

import javax.batch.api.ItemReadListener;

import com.ibm.batch.container.exception.BatchContainerRuntimeException;

public class ItemReadListenerProxy extends AbstractProxy<ItemReadListener> implements ItemReadListener{

    ItemReadListenerProxy(ItemReadListener delegate) { 
        super(delegate);
    }

    @Override
    public void afterRead(Object item) {
        
        try {
            this.delegate.afterRead(item);
        } catch (Exception e) {
            throw new BatchContainerRuntimeException(e);
        }
    }

    @Override
    public void beforeRead() {
     
        try {
            this.delegate.beforeRead();
        } catch (Exception e) {
            throw new BatchContainerRuntimeException(e);
        }
    }

    @Override
    public void onReadError(Exception ex) {
        
        try {
            this.delegate.onReadError(ex);
        } catch (Exception e) {
            throw new BatchContainerRuntimeException(e);
        }
    }

}
