/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.ning.metrics.serialization.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FileSystemAccess
{
    private static final Logger log = LoggerFactory.getLogger(FileSystemAccess.class);

    private final Configuration hdfsConfig;
    private final Class<? extends FileSystem> fsClass;
    private FileSystem fs = null;
    private final Object connectionLock = new Object();

    private static final long START_WAIT_INTERVAL = 1000; // 1 second
    private static final long MAX_WAIT_TIME = 600000; // 10 minutes

    public FileSystemAccess(final Configuration hdfsConfig)
    {
        this(hdfsConfig, FileSystem.class);
    }

    public FileSystemAccess(final Configuration hdfsConfig, final Class<? extends FileSystem> fsClass)
    {
        this.hdfsConfig = hdfsConfig;
        this.fsClass = fsClass;
    }

    public void close()
    {
        try {
            fs.close();
        }
        catch (IOException e) {
            log.warn("Unable to properly shutdown connection to HDFS", e);
        }
    }

    public FileSystem get() throws IOException
    {
        return get(MAX_WAIT_TIME);
    }

    public FileSystem get(final long maxWaitTimeMillis) throws IOException
    {
        try {
            if (fs == null) {
                synchronized (connectionLock) {
                    if (fs == null) {
                        try {
                            setFileSystem();
                        }
                        catch (IOException e) {
                            log.warn("Got I/O exception when setting up HDFS for the first time: {}", e.toString());
                        }
                    }
                }
            }
            return getFileSystemSafe();
        }
        catch (IOException e) {
            log.warn("Got IOException trying to connect to HDFS: {}", e.toString());

            synchronized (connectionLock) {
                long waitTime = START_WAIT_INTERVAL;
                long timeSpentWaiting = 0;
                while (true) {
                    try {
                        // try to set up the FileSystem
                        setFileSystem();
                        return getFileSystemSafe();
                    }
                    catch (IOException e1) {
                        // exp backoff
                        try {
                            log.warn("Sleeping {} ms before reconnecting to HDFS", waitTime);
                            Thread.sleep(waitTime);
                            timeSpentWaiting += waitTime;
                        }
                        catch (InterruptedException e2) {
                            e2.printStackTrace();
                        }

                        // give up after maxWaitTimeMillis
                        if (timeSpentWaiting >= maxWaitTimeMillis) {
                            throw new IOException(String.format("Cannot access File System. Gave up after trying for %d:%d", timeSpentWaiting / 60000, timeSpentWaiting / 1000 % 60));
                        }

                        waitTime *= 2;

                        if (timeSpentWaiting + waitTime > maxWaitTimeMillis) {
                            // check
                            waitTime = maxWaitTimeMillis - timeSpentWaiting;
                        }
                    }
                }
            }
        }
    }

    // throws an IOException if the current FileSystem isn't working

    private FileSystem getFileSystemSafe() throws IOException
    {
        try {
            fs.getFileStatus(new Path("/"));
            return fs;
        }
        catch (NullPointerException e) {
            throw new IOException("file system not initialized");
        }
    }

    private void setFileSystem() throws IOException
    {
        try {
            final Method getMethod = fsClass.getMethod("get", Configuration.class);
            fs = (FileSystem) getMethod.invoke(null, hdfsConfig);
        }
        catch (NoSuchMethodException e) {
            throw new IOException(String.format("Class %s doesn't have a get method", fsClass), e);
        }
        catch (InvocationTargetException e) {
            throw new IOException(String.format("Got exception while accessing get method for class %s ", fsClass), e);
        }
        catch (IllegalAccessException e) {
            throw new IOException(String.format("Got exception while accessing get method for class %s ", fsClass), e);
        }
        catch (RuntimeException e) {
            throw new IOException(String.format("Got exception while accessing get method for class %s ", fsClass), e);
        }
    }
}
