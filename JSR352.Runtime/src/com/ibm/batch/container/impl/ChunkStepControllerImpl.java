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
package com.ibm.batch.container.impl;

import java.io.ByteArrayInputStream;
import java.io.Externalizable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.batch.api.CheckpointAlgorithm;
import javax.batch.operations.JobOperator.BatchStatus;

import jsr352.batch.jsl.Chunk;
import jsr352.batch.jsl.ItemProcessor;
import jsr352.batch.jsl.ItemReader;
import jsr352.batch.jsl.ItemWriter;
import jsr352.batch.jsl.Property;
import jsr352.batch.jsl.Step;

import com.ibm.batch.container.artifact.proxy.CheckpointAlgorithmProxy;
import com.ibm.batch.container.artifact.proxy.ChunkListenerProxy;
import com.ibm.batch.container.artifact.proxy.InjectionReferences;
import com.ibm.batch.container.artifact.proxy.ItemProcessListenerProxy;
import com.ibm.batch.container.artifact.proxy.ItemProcessorProxy;
import com.ibm.batch.container.artifact.proxy.ItemReadListenerProxy;
import com.ibm.batch.container.artifact.proxy.ItemReaderProxy;
import com.ibm.batch.container.artifact.proxy.ItemWriteListenerProxy;
import com.ibm.batch.container.artifact.proxy.ItemWriterProxy;
import com.ibm.batch.container.artifact.proxy.ProxyFactory;
import com.ibm.batch.container.artifact.proxy.RetryProcessListenerProxy;
import com.ibm.batch.container.artifact.proxy.RetryReadListenerProxy;
import com.ibm.batch.container.artifact.proxy.RetryWriteListenerProxy;
import com.ibm.batch.container.artifact.proxy.SkipProcessListenerProxy;
import com.ibm.batch.container.artifact.proxy.SkipReadListenerProxy;
import com.ibm.batch.container.artifact.proxy.SkipWriteListenerProxy;
import com.ibm.batch.container.context.impl.MetricImpl;
import com.ibm.batch.container.exception.BatchContainerRuntimeException;
import com.ibm.batch.container.exception.BatchContainerServiceException;
import com.ibm.batch.container.jobinstance.RuntimeJobExecutionImpl;
import com.ibm.batch.container.services.IPersistenceManagerService;
import com.ibm.batch.container.services.ServicesManager;
import com.ibm.batch.container.services.ServicesManager.ServiceType;
import com.ibm.batch.container.util.PartitionDataWrapper;
import com.ibm.batch.container.util.TCCLObjectInputStream;
import com.ibm.batch.container.util.PartitionDataWrapper.PartitionEventType;
import com.ibm.batch.container.validation.ArtifactValidationException;
import com.ibm.ws.batch.container.checkpoint.CheckpointAlgorithmFactory;
import com.ibm.ws.batch.container.checkpoint.CheckpointData;
import com.ibm.ws.batch.container.checkpoint.CheckpointDataKey;
import com.ibm.ws.batch.container.checkpoint.CheckpointManager;
import com.ibm.ws.batch.container.checkpoint.ItemCheckpointAlgorithm;

public class ChunkStepControllerImpl extends SingleThreadedStepControllerImpl {

    private final static String sourceClass = ChunkStepControllerImpl.class.getName();
    private final static Logger logger = Logger.getLogger(sourceClass);

    private Chunk chunk = null;
    private ItemReaderProxy readerProxy = null;
    private ItemProcessorProxy processorProxy = null;
    private ItemWriterProxy writerProxy = null;
    private CheckpointAlgorithmProxy checkpointProxy = null;
    private CheckpointAlgorithm chkptAlg = null;
    private CheckpointManager checkpointManager;
    private ServicesManager servicesManager = ServicesManager.getInstance();
    private IPersistenceManagerService _persistenceManagerService = null;
    private SkipHandler skipHandler = null;
    CheckpointDataKey readerChkptDK, writerChkptDK = null;
    CheckpointData readerChkptData = null;
    CheckpointData writerChkptData = null;
    List<ChunkListenerProxy> chunkListeners = null;
    List<SkipProcessListenerProxy> skipProcessListeners = null;
    List<SkipReadListenerProxy> skipReadListeners = null;
    List<SkipWriteListenerProxy> skipWriteListeners = null;
    List<RetryProcessListenerProxy> retryProcessListeners = null;
    List<RetryReadListenerProxy> retryReadListeners = null;
    List<RetryWriteListenerProxy> retryWriteListeners = null;
    List<ItemReadListenerProxy> itemReadListeners = null;
    List<ItemProcessListenerProxy> itemProcessListeners = null;
    List<ItemWriteListenerProxy> itemWriteListeners = null;
    private RetryHandler retryHandler;

    // metrics
    long readCount = 0;
    long writeCount = 0;
    long readSkipCount = 0;
    long processSkipCount = 0;
    long writeSkipCount = 0;
    boolean rollbackRetry = false;

    public ChunkStepControllerImpl(RuntimeJobExecutionImpl jobExecutionImpl, Step step) {
        super(jobExecutionImpl, step);
        // TODO Auto-generated constructor stub
    }

    // TODO: complete refactoring, remove starts/end comment and remove old
    // read-process-write loop
    // mostly works but a few failures like
    // " WRITE: the chunk write did not at the correct boundry (idx) ->11"
    // need to debug some more
    /*
     * Refactoring starts here
     */

    /**
     * Utility Class to hold statuses at each level of Read-Process-Write loop
     * 
     */
    private class ItemStatus {

        public boolean isSkipped() {
            return skipped;
        }

        public void setSkipped(boolean skipped) {
            this.skipped = skipped;
        }

        public boolean isFiltered() {
            return filtered;
        }

        public void setFiltered(boolean filtered) {
            this.filtered = filtered;
        }

        public boolean isCheckPointed() {
            return checkPointed;
        }

        public void setCheckPointed(boolean checkPointed) {
            this.checkPointed = checkPointed;
        }

        public boolean isFinished() {
            return finished;
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }

        public boolean isRetry() {
            return retry;
        }

        public void setRetry(boolean retry) {
            this.retry = retry;
        }

        public boolean isRollback() {
            return rollback;
        }

        public void setRollback(boolean rollback) {
            this.rollback = rollback;
        }

        private boolean skipped = false;
        private boolean filtered = false;
        private boolean finished = false;
        private boolean checkPointed = false;
        private boolean retry = false;
        private boolean rollback = false;

    }

    /**
     * We read and process one item at a time but write in chunks (group of
     * items). So, this method loops until we either reached the end of the
     * reader (not more items to read), or the writer buffer is full or a
     * checkpoint is triggered.
     * 
     * @param chunkSize
     *            write buffer size
     * @param theStatus
     *            flags when the read-process reached the last record or a
     *            checkpoint is required
     * @return an array list of objects to write
     */
    private List<Object> readAndProcess(int chunkSize, ItemStatus theStatus) {
        logger.entering(sourceClass, "readAndProcess", new Object[] { chunkSize, theStatus });

        List<Object> chunkToWrite = new ArrayList<Object>();
        Object itemRead = null;
        Object itemProcessed = null;
        int readProcessedCount = 0;

        while (true) {
            ItemStatus status = new ItemStatus();
            itemRead = readItem(status);

            if (status.isRollback()) {
                theStatus.setRollback(true);
                // inc rollbackCount
                stepContext.getMetric(MetricImpl.Counter.valueOf("ROLLBACK_COUNT")).incValue();
                break;
            }

            if (!status.isSkipped() && !status.isFinished()) {
                itemProcessed = processItem(itemRead, status);

                if (status.isRollback()) {
                    theStatus.setRollback(true);
                    // inc rollbackCount
                    stepContext.getMetric(MetricImpl.Counter.valueOf("ROLLBACK_COUNT")).incValue();
                    break;
                }

                if (!status.isSkipped() && !status.isFiltered()) {
                    chunkToWrite.add(itemProcessed);
                    readProcessedCount++;
                }
            }

            theStatus.setFinished(status.isFinished());
            theStatus.setCheckPointed(checkpointManager.ApplyCheckPointPolicy());

            // This will force the current item to finish processing on a stop
            // request
            if (stepContext.getBatchStatus().equals(BatchStatus.STOPPING)) {
                theStatus.setFinished(true);
            }

            // write buffer size reached
            if (readProcessedCount == chunkSize) {
                break;
            }

            // checkpoint reached
            if (theStatus.isCheckPointed()) {
                break;
            }

            // last record in readerProxy reached
            if (theStatus.isFinished()) {
                break;
            }

        }
        logger.exiting(sourceClass, "readAndProcess", chunkToWrite);
        return chunkToWrite;
    }

    /**
     * Reads an item from the reader
     * 
     * @param status
     *            flags the current read status
     * @return the item read
     */
    private Object readItem(ItemStatus status) {
        logger.entering(sourceClass, "readItem", status);
        Object itemRead = null;

        try {
            // call read listeners before and after the actual read
            for (ItemReadListenerProxy readListenerProxy : itemReadListeners) {
                readListenerProxy.beforeRead();
            }

            itemRead = readerProxy.readItem();

            for (ItemReadListenerProxy readListenerProxy : itemReadListeners) {
                readListenerProxy.afterRead(itemRead);
            }

            // itemRead == null means we reached the end of
            // the readerProxy "resultset"
            status.setFinished(itemRead == null);
            if (!status.isFinished()) {
                stepContext.getMetric(MetricImpl.Counter.valueOf("READ_COUNT")).incValue();
            }
        } catch (Exception e) {
        	if(!rollbackRetry) {
        		if (retryReadException(e)) {
    				 // if not a rollback exception, just retry the current item
        			 if (!retryHandler.isRollbackException(e)) {
                         itemRead = readItem(status);
        			 } else {
                         status.setRollback(true);
                         rollbackRetry = true;
                         // inc rollbackCount
                         stepContext.getMetric(MetricImpl.Counter.valueOf("ROLLBACK_COUNT")).incValue();
                     }
        		}
        		else if(skipReadException(e)) {
        			status.setSkipped(true);
                    stepContext.getMetric(MetricImpl.Counter.valueOf("READ_SKIP_COUNT")).incValue();

        		}
        		else {
                    throw new BatchContainerRuntimeException(e);
                }
        	}
        	else {
        		// coming from a rollback retry
        		if(skipReadException(e)) {
        			status.setSkipped(true);
                    stepContext.getMetric(MetricImpl.Counter.valueOf("READ_SKIP_COUNT")).incValue();

        		}
        		else if (retryReadException(e)) {
        			 if (!retryHandler.isRollbackException(e)) {
                         itemRead = readItem(status);
        			 }
                     else {
                         status.setRollback(true);
                         // inc rollbackCount
                         stepContext.getMetric(MetricImpl.Counter.valueOf("ROLLBACK_COUNT")).incValue();
                     }
        		}
        		else {
                    throw new BatchContainerRuntimeException(e);
                }
        	}

        } catch (Throwable e) {
            throw new BatchContainerRuntimeException(e);
        }

        logger.exiting(sourceClass, "readItem", itemRead);
        return itemRead;
    }

    /**
     * Process an item previously read by the reader
     * 
     * @param itemRead
     *            the item read
     * @param status
     *            flags the current process status
     * @return the processed item
     */
    private Object processItem(Object itemRead, ItemStatus status) {
        logger.entering(sourceClass, "processItem", new Object[] { itemRead, status });
        Object processedItem = null;

        try {

            // call process listeners before and after the actual process call
            for (ItemProcessListenerProxy processListenerProxy : itemProcessListeners) {
                processListenerProxy.beforeProcess(itemRead);
            }

            processedItem = processorProxy.processItem(itemRead);

            if (processedItem == null) {
                // inc filterCount
                stepContext.getMetric(MetricImpl.Counter.valueOf("FILTER_COUNT")).incValue();
                status.setFiltered(true);
            }

            for (ItemProcessListenerProxy processListenerProxy : itemProcessListeners) {
                processListenerProxy.afterProcess(itemRead, processedItem);
            }
        } catch (Exception e) {
        	
        	if(!rollbackRetry) {
        		if (retryProcessException(e, itemRead)) {
        			if (!retryHandler.isRollbackException(e)) {
                        // call process listeners before and after the actual
                        // process call
                        for (ItemProcessListenerProxy processListenerProxy : itemProcessListeners) {
                            processListenerProxy.beforeProcess(itemRead);
                        }
                        processedItem = processItem(itemRead, status);
                        if (processedItem == null) {
                            // inc filterCount
                            stepContext.getMetric(MetricImpl.Counter.valueOf("FILTER_COUNT")).incValue();
                            status.setFiltered(true);
                        }

                        for (ItemProcessListenerProxy processListenerProxy : itemProcessListeners) {
                            processListenerProxy.afterProcess(itemRead, processedItem);
                        }
                    } else {
                        status.setRollback(true);
                        rollbackRetry = true;
                        // inc rollbackCount
                        stepContext.getMetric(MetricImpl.Counter.valueOf("ROLLBACK_COUNT")).incValue();
                    }
        		}
        		else if (skipProcessException(e, itemRead)) {
        			status.setSkipped(true);
                    stepContext.getMetric(MetricImpl.Counter.valueOf("PROCESS_SKIP_COUNT")).incValue();
        		}
        		else {
                    throw new BatchContainerRuntimeException(e);
                }
        	}
        	else {
        		if (skipProcessException(e, itemRead)) {
        			status.setSkipped(true);
                    stepContext.getMetric(MetricImpl.Counter.valueOf("PROCESS_SKIP_COUNT")).incValue();
                 } else if (retryProcessException(e, itemRead)) {
        			if (!retryHandler.isRollbackException(e)) {
                        // call process listeners before and after the actual
                        // process call
                        for (ItemProcessListenerProxy processListenerProxy : itemProcessListeners) {
                            processListenerProxy.beforeProcess(itemRead);
                        }
                        processedItem = processItem(itemRead, status);
                        if (processedItem == null) {
                            // inc filterCount
                            stepContext.getMetric(MetricImpl.Counter.valueOf("FILTER_COUNT")).incValue();
                            status.setFiltered(true);
                        }

                        for (ItemProcessListenerProxy processListenerProxy : itemProcessListeners) {
                            processListenerProxy.afterProcess(itemRead, processedItem);
                        }
                    } else {
                        status.setRollback(true);
                        rollbackRetry = true;
                        // inc rollbackCount
                        stepContext.getMetric(MetricImpl.Counter.valueOf("ROLLBACK_COUNT")).incValue();
                    }
        		} else {
                     throw new BatchContainerRuntimeException(e);
                 }
        	}

        } catch (Throwable e) {
            throw new BatchContainerRuntimeException(e);
        }

        logger.exiting(sourceClass, "processItem", processedItem);
        return processedItem;
    }

    /**
     * Writes items
     * 
     * @param theChunk
     *            the array list with all items processed ready to be written
     */
    private void writeChunk(List<Object> theChunk, ItemStatus status) {
        logger.entering(sourceClass, "writeChunk", theChunk);
        if (!theChunk.isEmpty()) {
            try {

                // call read listeners before and after the actual read
                for (ItemWriteListenerProxy writeListenerProxy : itemWriteListeners) {
                    writeListenerProxy.beforeWrite(theChunk);
                }

                writerProxy.writeItems(theChunk);

                for (ItemWriteListenerProxy writeListenerProxy : itemWriteListeners) {
                    writeListenerProxy.afterWrite(theChunk);
                }
                stepContext.getMetric(MetricImpl.Counter.valueOf("WRITE_COUNT")).incValueBy(theChunk.size());
            } catch (Exception e) {

            	if(!rollbackRetry)
            	{
            		if (retryWriteException(e, theChunk)) {
                        if (!retryHandler.isRollbackException(e)) {
                            writeChunk(theChunk, status);
                        } else {
                        	rollbackRetry = true;
                            status.setRollback(true);
                            // inc rollbackCount
                            stepContext.getMetric(MetricImpl.Counter.valueOf("ROLLBACK_COUNT")).incValue();
                        }
                    } else if (skipWriteException(e, theChunk)) {
                        stepContext.getMetric(MetricImpl.Counter.valueOf("WRITE_SKIP_COUNT")).incValueBy(1);
                    } else {
                        throw new BatchContainerRuntimeException(e);
                    }
            		
            	}
            	else {
            		if (skipWriteException(e, theChunk)) {
                        stepContext.getMetric(MetricImpl.Counter.valueOf("WRITE_SKIP_COUNT")).incValueBy(1);
                    } else if (retryWriteException(e, theChunk)) {
                        if (!retryHandler.isRollbackException(e)) {
                        	status.setRetry(true);
                            writeChunk(theChunk, status);
                        } else {
                        	rollbackRetry = true;
                            status.setRollback(true);
                            // inc rollbackCount
                            stepContext.getMetric(MetricImpl.Counter.valueOf("ROLLBACK_COUNT")).incValue();
                        }
                    } else {
                        throw new BatchContainerRuntimeException(e);
                    }
            	}

            } catch (Throwable e) {
                throw new BatchContainerRuntimeException(e);
            }
        }
        logger.exiting(sourceClass, "writeChunk");
    }

    /**
     * Main Read-Process-Write loop
     * 
     * @throws Exception
     */
    private void invokeChunk() throws Exception {
        logger.entering(sourceClass, "invokeChunk2");

        int itemCount = ChunkHelper.getItemCount(chunk);
        int timeInterval = ChunkHelper.getTimeLimit(chunk);
        List<Object> chunkToWrite = new ArrayList<Object>();
        boolean checkPointed = true;
        boolean rollback = false;

        // begin new transaction at first iteration or after a checkpoint commit

        try {

            transactionManager.begin();
            this.openReaderAndWriter();
            transactionManager.commit();

            while (true) {
            	
                if (checkPointed || rollback) {
                    transactionManager.begin();
                    for (ChunkListenerProxy chunkProxy : chunkListeners) {
                        chunkProxy.beforeChunk();
                    }
                    
                    if (rollback) {
                        positionReaderAtCheckpoint();
                        positionWriterAtCheckpoint();
                        checkpointManager = new CheckpointManager(readerProxy, writerProxy,
                                getCheckpointAlgorithm(itemCount, timeInterval), jobExecutionImpl.getExecutionId(), jobExecutionImpl
                                        .getJobInstance().getInstanceId(), step.getId());
                    }
                }
              
                ItemStatus status = new ItemStatus();
                
                if (rollback) {
                    rollback = false;
                }

                chunkToWrite = readAndProcess(itemCount, status);

                if (status.isRollback()) {
                    itemCount = 1;
                    rollback = true;
                    transactionManager.rollback();
                    continue;
                }
                
                writeChunk(chunkToWrite, status);
                
                if (status.isRollback()) {
                    itemCount = 1;
                    rollback = true;
                    transactionManager.rollback();
                    continue;
                }
                checkPointed = status.isCheckPointed();

                // we could finish the chunk in 3 conditions: buffer is full,
                // checkpoint, not more input
                if (status.isCheckPointed() || status.isFinished()) {
                    // TODO: missing before checkpoint listeners
                    // 1.- check if spec list proper steps for before checkpoint
                    // 2.- ask Andy about retry
                    // 3.- when do we stop?

                    checkpointManager.checkpoint();

                    for (ChunkListenerProxy chunkProxy : chunkListeners) {
                        chunkProxy.afterChunk();
                    }
                    
                    transactionManager.commit();
                    if (collectorProxy != null) {

                        Externalizable data = this.collectorProxy.collectPartitionData();

                        if (this.analyzerQueue != null) {
                            // Invoke the partition analayzer at the end of each step if
                            // the step runs

                            PartitionDataWrapper dataWrapper = new PartitionDataWrapper();
                            dataWrapper.setCollectorData(data);
                            dataWrapper.setEventType(PartitionEventType.ANALYZE_COLLECTOR_DATA);
                            analyzerQueue.add(dataWrapper);
                        }

                    }                                        
                    
                    // exit loop when last record is written
                    if (status.isFinished()) {
                        transactionManager.begin();
                        
                        readerProxy.close();
                        writerProxy.close();
                        
                        transactionManager.commit();
                        // increment commitCount
                        stepContext.getMetric(MetricImpl.Counter.valueOf("COMMIT_COUNT")).incValue();
                        break;
                    } else {
                        // increment commitCount
                        stepContext.getMetric(MetricImpl.Counter.valueOf("COMMIT_COUNT")).incValue();
                    }

                }

            }
            
        } catch (Throwable e) {
        	for (ChunkListenerProxy chunkProxy : chunkListeners) {
                chunkProxy.onError();
            }
            transactionManager.rollback();
            logger.log(Level.SEVERE, "Failure in Read-Process-Write Loop, transaction is being rolled back.", e);
            throw new BatchContainerRuntimeException(e);
        }
        logger.exiting(sourceClass, "invokeChunk");
    }

    protected void invokeCoreStep() throws BatchContainerServiceException {

        this.chunk = step.getChunk();

        initializeChunkArtifacts();

        try {
            invokeChunk();
        } catch (Exception re) {
            throw new BatchContainerServiceException(re);
        } finally {
            if (collectorProxy != null) {

                Externalizable data = this.collectorProxy.collectPartitionData();

                if (this.analyzerQueue != null) {
                    // Invoke the partition analayzer at the end of each step if
                    // the step runs

                    PartitionDataWrapper dataWrapper = new PartitionDataWrapper();
                    dataWrapper.setCollectorData(data);
                    dataWrapper.setEventType(PartitionEventType.ANALYZE_COLLECTOR_DATA);
                    analyzerQueue.add(dataWrapper);
                }

            }

            if (this.analyzerQueue != null) {
                PartitionDataWrapper dataWrapper = new PartitionDataWrapper();
                dataWrapper.setBatchStatus(stepStatus.getBatchStatus());
                dataWrapper.setExitStatus(stepStatus.getExitStatus());
                dataWrapper.setEventType(PartitionEventType.ANALYZE_STATUS);
                analyzerQueue.add(dataWrapper);
            }
        }

        // TODO invoke analyzeExitStatus in analyzer if it exists
    }

    private CheckpointAlgorithm getCheckpointAlgorithm(int itemCount, int timeInterval) {
        CheckpointAlgorithm alg = null;

        if (checkpointProxy.getCheckpointType() == "item") {
            alg = new ItemCheckpointAlgorithm();
            ((ItemCheckpointAlgorithm) alg).setThresholds(itemCount, timeInterval);
        } else { // custom chkpt alg
            alg = (CheckpointAlgorithm) checkpointProxy;
        }

        return alg;
    }

    /*
     * Initialize itemreader, itemwriter, and item processor checkpoint
     */
    private void initializeChunkArtifacts() {
        String sourceMethod = "initializeChunkArtifacts";
        if (logger.isLoggable(Level.FINE))
            logger.entering(sourceClass, sourceMethod);

        ItemReader itemReader = chunk.getReader();
        List<Property> itemReaderProps = itemReader.getProperties() == null ? null : itemReader.getProperties().getPropertyList();
        try {
            InjectionReferences injectionRef = new InjectionReferences(jobExecutionImpl.getJobContext(), stepContext, 
                    itemReaderProps);

            readerProxy = ProxyFactory.createItemReaderProxy(itemReader.getRef(), injectionRef);

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Created ItemReaderProxy for " + itemReader.getRef());
            }
        } catch (ArtifactValidationException e) {
            throw new BatchContainerServiceException("Cannot create the ItemReader [" + itemReader.getRef() + "]", e);
        }

        ItemProcessor itemProcessor = chunk.getProcessor();
        List<Property> itemProcessorProps = itemProcessor.getProperties() == null ? null : itemProcessor.getProperties().getPropertyList();
        try {

            InjectionReferences injectionRef = new InjectionReferences(jobExecutionImpl.getJobContext(), stepContext, 
                    itemProcessorProps);

            processorProxy = ProxyFactory.createItemProcessorProxy(itemProcessor.getRef(), injectionRef);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Created ItemProcessorProxy for " + itemProcessor.getRef());
            }
        } catch (ArtifactValidationException e) {
            throw new BatchContainerServiceException("Cannot create the ItemProcessor [" + itemProcessor.getRef() + "]", e);
        }

        ItemWriter itemWriter = chunk.getWriter();
        List<Property> itemWriterProps = itemWriter.getProperties() == null ? null : itemWriter.getProperties().getPropertyList();
        try {
            InjectionReferences injectionRef = new InjectionReferences(jobExecutionImpl.getJobContext(), stepContext, 
                    itemWriterProps);

            writerProxy = ProxyFactory.createItemWriterProxy(itemWriter.getRef(), injectionRef);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Created ItemWriterProxy for " + itemWriter.getRef());
            }
        } catch (ArtifactValidationException e) {
            throw new BatchContainerServiceException("Cannot create the ItemWriter [" + itemWriter.getRef() + "]", e);
        }

        try {
            List<Property> propList = null;

            if (chunk.getCheckpointAlgorithm() != null) {

                propList = (chunk.getCheckpointAlgorithm().getProperties() == null) ? null : chunk.getCheckpointAlgorithm().getProperties()
                        .getPropertyList();
            }

            InjectionReferences injectionRef = new InjectionReferences(jobExecutionImpl.getJobContext(), stepContext, 
                    propList);

            checkpointProxy = CheckpointAlgorithmFactory.getCheckpointAlgorithmProxy(step, injectionRef);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Created CheckpointAlgorithmProxy for policy [" + chunk.getCheckpointPolicy() + "]");
            }
        } catch (ArtifactValidationException e) {
            throw new BatchContainerServiceException("Cannot create the CheckpointAlgorithm for policy [" + chunk.getCheckpointPolicy()
                    + "]", e);
        }

        int itemCount = ChunkHelper.getItemCount(chunk);
        int timeInterval = ChunkHelper.getTimeLimit(chunk);

        InjectionReferences injectionRef = new InjectionReferences(jobExecutionImpl.getJobContext(), stepContext, 
                null);

        this.chunkListeners = jobExecutionImpl.getListenerFactory().getChunkListeners(step, injectionRef);
        this.skipProcessListeners = jobExecutionImpl.getListenerFactory().getSkipProcessListeners(step, injectionRef);
        this.skipReadListeners = jobExecutionImpl.getListenerFactory().getSkipReadListeners(step, injectionRef);
        this.skipWriteListeners = jobExecutionImpl.getListenerFactory().getSkipWriteListeners(step, injectionRef);
        this.retryProcessListeners = jobExecutionImpl.getListenerFactory().getRetryProcessListeners(step, injectionRef);
        this.retryReadListeners = jobExecutionImpl.getListenerFactory().getRetryReadListeners(step, injectionRef);
        this.retryWriteListeners = jobExecutionImpl.getListenerFactory().getRetryWriteListeners(step, injectionRef);
        this.itemReadListeners = jobExecutionImpl.getListenerFactory().getItemReadListeners(step, injectionRef);
        this.itemProcessListeners = jobExecutionImpl.getListenerFactory().getItemProcessListeners(step, injectionRef);
        this.itemWriteListeners = jobExecutionImpl.getListenerFactory().getItemWriteListeners(step, injectionRef);

        if (checkpointProxy.getCheckpointType() == "item") {
            chkptAlg = new ItemCheckpointAlgorithm();
            ((ItemCheckpointAlgorithm) chkptAlg).setThresholds(itemCount, timeInterval);
        } else { // custom chkpt alg
            chkptAlg = (CheckpointAlgorithm) checkpointProxy;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Setting contexts for chunk artifacts");
        }

        if (logger.isLoggable(Level.FINE))
            logger.fine("Initialize checkpoint manager with item-count=" + itemCount);
        logger.fine("Initialize checkpoint manager with time-interval=" + timeInterval);

        checkpointManager = new CheckpointManager(readerProxy, writerProxy, chkptAlg, jobExecutionImpl.getExecutionId(), jobExecutionImpl
                .getJobInstance().getInstanceId(), step.getId());

        skipHandler = new SkipHandler(chunk, jobExecutionImpl.getJobInstance().getInstanceId(), step.getId());
        skipHandler.addSkipProcessListener(skipProcessListeners);
        skipHandler.addSkipReadListener(skipReadListeners);
        skipHandler.addSkipWriteListener(skipWriteListeners);

        retryHandler = new RetryHandler(chunk, jobExecutionImpl.getJobInstance().getInstanceId(), step.getId());

        retryHandler.addRetryProcessListener(retryProcessListeners);
        retryHandler.addRetryReadListener(retryReadListeners);
        retryHandler.addRetryWriteListener(retryWriteListeners);

        if (logger.isLoggable(Level.FINE))
            logger.exiting(sourceClass, sourceMethod);
    }

    private void openReaderAndWriter() {
        String sourceMethod = "openReaderAndWriter";

        if (logger.isLoggable(Level.FINE))
            logger.entering(sourceClass, sourceMethod);

        _persistenceManagerService = (IPersistenceManagerService) servicesManager.getService(ServiceType.PERSISTENCE_MANAGEMENT_SERVICE);
        readerChkptDK = new CheckpointDataKey(jobExecutionImpl.getJobInstance().getInstanceId(), step.getId(), "READER");
        List<?> data = _persistenceManagerService.getData(IPersistenceManagerService.CHECKPOINT_STORE_ID, readerChkptDK);
        try {

            // check for data in backing store
            if (data.size() >= 1) {

                readerChkptData = (CheckpointData) data.get(0);
                byte[] readertoken = readerChkptData.getRestartToken();
                ByteArrayInputStream readerChkptBA = new ByteArrayInputStream(readertoken);
                TCCLObjectInputStream readerOIS = null;
                try {
                    readerOIS = new TCCLObjectInputStream(readerChkptBA);
                    readerProxy.open((Externalizable) readerOIS.readObject());
                    readerOIS.close();
                } catch (Exception ex) {
                    // is this what I should be throwing here?
                    throw new BatchContainerServiceException("Cannot persist the checkpoint data for [" + step.getId() + "]", ex);
                }
            } else {
                // no chkpt data exists in the backing store
                readerChkptData = null;
                readerProxy.open(null);
            }
        } catch (ClassCastException e) {
            throw new IllegalStateException("Expected CheckpointData but found" + data.get(0));
        }

        writerChkptDK = new CheckpointDataKey(jobExecutionImpl.getJobInstance().getInstanceId(), step.getId(), "WRITER");
        data = _persistenceManagerService.getData(IPersistenceManagerService.CHECKPOINT_STORE_ID, writerChkptDK);

        try {
            // check for data in backing store
            if (data.size() >= 1) {
                writerChkptData = (CheckpointData) data.get(0);
                byte[] writertoken = writerChkptData.getRestartToken();
                ByteArrayInputStream writerChkptBA = new ByteArrayInputStream(writertoken);
                TCCLObjectInputStream writerOIS = null;
                try {
                    writerOIS = new TCCLObjectInputStream(writerChkptBA);
                    writerProxy.open((Externalizable) writerOIS.readObject());
                    writerOIS.close();
                } catch (Exception ex) {
                    // is this what I should be throwing here?
                    throw new BatchContainerServiceException("Cannot persist the checkpoint data for [" + step.getId() + "]", ex);
                }
            } else {
                // no chkpt data exists in the backing store
                writerChkptData = null;
                writerProxy.open(null);
            }
        } catch (ClassCastException e) {
            throw new IllegalStateException("Expected Checkpoint but found" + data.get(0));
        }

        // set up metrics
        // stepContext.addMetric(MetricImpl.Counter.valueOf("READ_COUNT"), 0);
        // stepContext.addMetric(MetricImpl.Counter.valueOf("WRITE_COUNT"), 0);
        // stepContext.addMetric(MetricImpl.Counter.valueOf("READ_SKIP_COUNT"),
        // 0);
        // stepContext.addMetric(MetricImpl.Counter.valueOf("PROCESS_SKIP_COUNT"),
        // 0);
        // stepContext.addMetric(MetricImpl.Counter.valueOf("WRITE_SKIP_COUNT"),
        // 0);

        if (logger.isLoggable(Level.FINE))
            logger.exiting(sourceClass, sourceMethod);
    }

    @Override
    public void stop() {
        stepContext.setBatchStatus(BatchStatus.STOPPING);

        // we don't need to call stop on the chunk implementation here since a
        // chunk always returns control to
        // the batch container after every item.

    }

    boolean skipReadException(Exception e) {

        try {
            skipHandler.handleExceptionRead(e);
        } catch (BatchContainerRuntimeException bcre) {
            return false;
        }

        return true;

    }

    boolean retryReadException(Exception e) {

        try {
            retryHandler.handleExceptionRead(e);
        } catch (BatchContainerRuntimeException bcre) {
            return false;
        }

        return true;

    }

    boolean skipProcessException(Exception e, Object record) {

        try {
            skipHandler.handleExceptionWithRecordProcess(e, record);
        } catch (BatchContainerRuntimeException bcre) {
            return false;
        }

        return true;

    }

    boolean retryProcessException(Exception e, Object record) {

        try {
            retryHandler.handleExceptionProcess(e, record);
        } catch (BatchContainerRuntimeException bcre) {
            return false;
        }

        return true;

    }

    boolean skipWriteException(Exception e, List<Object> chunkToWrite) {

        Object writeObjs[] = chunkToWrite.toArray();
        for (int i = 0; i < writeObjs.length; i++) {
            try {
                skipHandler.handleExceptionWithRecordListWrite(e, chunkToWrite);
            } catch (BatchContainerRuntimeException bcre) {
                return false;
            }
        }

        return true;

    }

    boolean retryWriteException(Exception e, List<Object> chunkToWrite) {

        try {
            retryHandler.handleExceptionWrite(e, chunkToWrite);
        } catch (BatchContainerRuntimeException bcre) {
            return false;
        }

        return true;

    }

    private void positionReaderAtCheckpoint() {
        _persistenceManagerService = (IPersistenceManagerService) servicesManager.getService(ServiceType.PERSISTENCE_MANAGEMENT_SERVICE);
        readerChkptDK = new CheckpointDataKey(jobExecutionImpl.getJobInstance().getInstanceId(), step.getId(), "READER");
        List<?> data = _persistenceManagerService.getData(IPersistenceManagerService.CHECKPOINT_STORE_ID, readerChkptDK);

        CheckpointData readerData = null;

        try {
            // check for data in backing store
            if (data.size() >= 1) {

                readerData = (CheckpointData) data.get(0);
                byte[] readertoken = readerData.getRestartToken();
                ByteArrayInputStream readerChkptBA = new ByteArrayInputStream(readertoken);
                TCCLObjectInputStream readerOIS = null;
                try {
                    readerOIS = new TCCLObjectInputStream(readerChkptBA);
                    readerProxy.open((Externalizable) readerOIS.readObject());
                    readerOIS.close();
                } catch (Exception ex) {
                    // is this what I should be throwing here?
                    throw new BatchContainerServiceException("Cannot persist the checkpoint data for [" + step.getId() + "]", ex);
                }
            } else {
                // no chkpt data exists in the backing store
                readerData = null;
                readerProxy.open(null);
            }
        } catch (ClassCastException e) {
            throw new IllegalStateException("Expected CheckpointData but found" + data.get(0));
        }
    }

    private void positionWriterAtCheckpoint() {
        _persistenceManagerService = (IPersistenceManagerService) servicesManager.getService(ServiceType.PERSISTENCE_MANAGEMENT_SERVICE);
        writerChkptDK = new CheckpointDataKey(jobExecutionImpl.getJobInstance().getInstanceId(), step.getId(), "WRITER");
        List<?> data = _persistenceManagerService.getData(IPersistenceManagerService.CHECKPOINT_STORE_ID, writerChkptDK);

        CheckpointData writerData = null;

        try {
            // check for data in backing store
            if (data.size() >= 1) {

                writerData = (CheckpointData) data.get(0);
                byte[] writertoken = writerData.getRestartToken();
                ByteArrayInputStream writerChkptBA = new ByteArrayInputStream(writertoken);
                TCCLObjectInputStream writerOIS = null;
                try {
                    writerOIS = new TCCLObjectInputStream(writerChkptBA);
                    writerProxy.open((Externalizable) writerOIS.readObject());
                    writerOIS.close();
                } catch (Exception ex) {
                    // is this what I should be throwing here?
                    throw new BatchContainerServiceException("Cannot persist the checkpoint data for [" + step.getId() + "]", ex);
                }
            } else {
                // no chkpt data exists in the backing store
                writerData = null;
                writerProxy.open(null);
            }
        } catch (ClassCastException e) {
            throw new IllegalStateException("Expected CheckpointData but found" + data.get(0));
        }
    }
}
