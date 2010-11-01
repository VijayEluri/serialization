/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.metrics.serialization.writer;

import com.ning.metrics.serialization.event.Event;
import com.ning.metrics.serialization.event.StubEvent;
import com.ning.metrics.serialization.event.ThriftEnvelopeEvent;
import com.ning.metrics.serialization.thrift.ThriftEnvelope;
import com.ning.metrics.serialization.thrift.ThriftField;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TestDiskSpoolEventWriter
{
    private static Logger log = Logger.getLogger(TestDiskSpoolEventWriter.class);

    private Runnable commandToRun;
    private long secondsToWait;
    private ScheduledExecutorService executor = new StubScheduledExecutorService()
    {
        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
        {
            commandToRun = command;
            secondsToWait = TimeUnit.SECONDS.convert(delay, unit);

            return null;
        }
    };
    private File spoolDir;
    private File tmpDir;
    private File quarantineDir;
    private final EventHandler writerThrowsIOExceptionOnCommit = new StubEventHandler(new MockEventWriter(true, false, false));
    private final EventHandler writerThrowsIOExceptionOnWrite = new StubEventHandler(new MockEventWriter(false, false, true));
    private final EventHandler writerThrowsIOExceptionOnCommitAndRollback = new StubEventHandler(new MockEventWriter(true, true, false));
    private final EventHandler writerThrowsIOExceptionOnAll = new StubEventHandler(new MockEventWriter(true, true, true));
    private final EventHandler writerSucceeds = new StubEventHandler();
    private final Event eventThrowsOnWrite = new StubEvent()
    {
        @Override
        public void writeExternal(ObjectOutput out) throws IOException
        {
            throw new IOException();
        }
    };
    private String spoolPath;

    @BeforeMethod(alwaysRun = true)
    void setup()
    {
        spoolPath = "/var/tmp/serialization/test/disk-spool-event-writer-" + System.currentTimeMillis();
        spoolDir = new File(spoolPath);
        tmpDir = new File(spoolPath + "/_tmp");
        quarantineDir = new File(spoolPath + "/_quarantine");

        prepareSpoolDirs();
    }

    @Test(groups = "fast")
    public void testWriteIOFailure() throws Exception
    {
        DiskSpoolEventWriter writer = createWriter(writerSucceeds);

        try {
            writer.write(eventThrowsOnWrite);
            Assert.fail("expected IOException");
        }
        catch (IOException e) {
            Assert.assertEquals(e.getClass(), IOException.class);
        }

        testSpoolDirs(1, 0, 0);
        writer.rollback();
        testSpoolDirs(0, 0, 1);
    }

    @Test(groups = "fast")
    public void testNoBusyWait() throws Exception
    {
        final AtomicReference<List<File>> spooledFileList = new AtomicReference<List<File>>(Collections.<File>emptyList());

        @SuppressWarnings({"UnusedDeclaration"})
        DiskSpoolEventWriter writer = new DiskSpoolEventWriter(writerSucceeds, spoolPath, true, 30, executor, SyncType.NONE, 1)
        {
            @Override
            protected List<File> getSpooledFileList()
            {
                return spooledFileList.get();
            }
        };
        //will have empty files in spooled dir
        commandToRun.run();
        Assert.assertEquals(secondsToWait, 30);

        spooledFileList.set(Arrays.asList(new File("/tmp/fuu")));
        commandToRun.run();
        Assert.assertEquals(secondsToWait, 0);
    }

    @Test(groups = "fast")
    public void testDiskSpoolPersistentWriterSucceeds() throws Exception
    {
        DiskSpoolEventWriter writer = createWriter(writerSucceeds);

        testSpoolDirs(0, 0, 0);
        writer.write(createThriftEnvelopeEvent());
        testSpoolDirs(1, 0, 0);
        writer.commit();
        testSpoolDirs(0, 1, 0);
        commandToRun.run();
        testSpoolDirs(0, 0, 0);
    }

    @Test(groups = "fast")
    public void testPerisistentWriteFails() throws Exception
    {
        DiskSpoolEventWriter writer = createWriter(writerThrowsIOExceptionOnWrite);

        testSpoolDirs(0, 0, 0);
        writer.write(createThriftEnvelopeEvent());
        testSpoolDirs(1, 0, 0);
        writer.commit();
        commandToRun.run();
        testSpoolDirs(0, 0, 1);
    }

    @Test(groups = "fast")
    public void testPersisentCommitFails() throws Exception
    {
        DiskSpoolEventWriter writer = createWriter(writerThrowsIOExceptionOnCommit);

        prepareSpoolDirs();
        testSpoolDirs(0, 0, 0);
        writer.write(createThriftEnvelopeEvent());
        testSpoolDirs(1, 0, 0);
        writer.commit();
        testSpoolDirs(0, 1, 0);
        commandToRun.run();
        testSpoolDirs(0, 0, 1);
    }

    @Test(groups = "fast")
    public void testPersistentRollbackFails() throws Exception
    {
        DiskSpoolEventWriter writer = createWriter(writerThrowsIOExceptionOnCommitAndRollback);

        prepareSpoolDirs();
        testSpoolDirs(0, 0, 0);
        writer.write(createThriftEnvelopeEvent());
        testSpoolDirs(1, 0, 0);
        writer.commit();
        testSpoolDirs(0, 1, 0);
        commandToRun.run();
        testSpoolDirs(0, 0, 1);
    }

    @Test(groups = "fast")
    public void testPersisentMassiveFail() throws Exception
    {
        DiskSpoolEventWriter writer = createWriter(writerThrowsIOExceptionOnAll);

        prepareSpoolDirs();
        testSpoolDirs(0, 0, 0);
        writer.write(createThriftEnvelopeEvent());
        testSpoolDirs(1, 0, 0);
        writer.commit();
        testSpoolDirs(0, 1, 0);
        commandToRun.run();
        testSpoolDirs(0, 0, 1);
    }

    private DiskSpoolEventWriter createWriter(EventHandler persistentWriter)
    {
        return new DiskSpoolEventWriter(persistentWriter, spoolPath, true, 1, executor, SyncType.NONE, 1);
    }

    private void testSpoolDirs(int tmpCount, int spoolCount, int quarantineCount)
    {
        Assert.assertEquals(listThriftFiles(tmpDir).length, tmpCount);
        Assert.assertEquals(listThriftFiles(spoolDir).length, spoolCount);
        Assert.assertEquals(listThriftFiles(quarantineDir).length, quarantineCount);
    }

    private void prepareSpoolDirs()
    {
        cleanDirectory(spoolDir);
        cleanDirectory(tmpDir);
        cleanDirectory(quarantineDir);
    }

    private File[] listThriftFiles(File dir)
    {
        return dir.listFiles(new FileFilter()
        {
            @Override
            public boolean accept(File pathname)
            {
                return pathname.getName().endsWith(".thrift");
            }
        });
    }

    private void cleanDirectory(File quarantineDir)
    {
        if (quarantineDir.exists()) {
            for (File file : quarantineDir.listFiles()) {
                if (file.isFile()) {
                    if (!file.delete()) {
                        log.info(String.format("unable to delete file %s", file));
                    }
                }
            }
        }
    }

    private ThriftEnvelopeEvent createThriftEnvelopeEvent()
    {
        return new ThriftEnvelopeEvent(new DateTime("2009-01-01"), new ThriftEnvelope("TestEvent", Arrays.<ThriftField>asList(
            ThriftField.createThriftField(true, (short) 1),
            ThriftField.createThriftField(100, (short) 2),
            ThriftField.createThriftField("text here", (short) 3)
        )));
    }

}