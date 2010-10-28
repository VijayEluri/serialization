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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

class SyncingObjectOutputter implements ObjectOutputter
{
    private final FileOutputStream out;
    private final ObjectOutputStream objectOut;

    public SyncingObjectOutputter(FileOutputStream out) throws IOException
    {
        this.out = out;
        this.objectOut = new ObjectOutputStream(out);
    }

    @Override
    public void writeObject(Object obj) throws IOException
    {
        objectOut.write(1);
        objectOut.writeObject(obj);
        objectOut.flush();
        out.getFD().sync();

    }

    @Override
    public void close() throws IOException
    {
        objectOut.close();
    }
}