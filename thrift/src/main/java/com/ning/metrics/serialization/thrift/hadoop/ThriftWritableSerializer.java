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

package com.ning.metrics.serialization.thrift.hadoop;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ThriftWritableSerializer
{
    private DataOutputStream dataOut;

    public void open(OutputStream out)
    {
        if (out instanceof DataOutputStream) {
            dataOut = (DataOutputStream) out;
        }
        else {
            dataOut = new DataOutputStream(out);
        }
    }

    public void serialize(ThriftWritable w) throws IOException
    {
        w.write(dataOut);
    }

    public void close() throws IOException
    {
        dataOut.close();
    }
}
