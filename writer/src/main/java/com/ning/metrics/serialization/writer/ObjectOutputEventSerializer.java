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
package com.ning.metrics.serialization.writer;

import com.ning.metrics.serialization.event.Event;
import com.ning.metrics.serialization.event.EventSerializer;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public class ObjectOutputEventSerializer implements EventSerializer
{
    private ObjectOutputStream objectOut;

    @Override
    public void open(final OutputStream out) throws IOException
    {
        objectOut = new ObjectOutputStream(out);
    }

    @Override
    public void serialize(final Event obj) throws IOException
    {
        objectOut.write(1);
        objectOut.writeObject(obj);
    }

    @Override
    public void close() throws IOException
    {
        objectOut.close();
    }
}
