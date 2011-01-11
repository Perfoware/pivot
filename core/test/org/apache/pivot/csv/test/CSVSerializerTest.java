/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pivot.csv.test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pivot.csv.CSVSerializer;
import org.apache.pivot.csv.CSVSerializerListener;
import org.apache.pivot.io.SerializationException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CSVSerializerTest {
    @Test
    @SuppressWarnings("unchecked")
    public void testBasicReadObject() throws IOException, SerializationException {
        // Test multiple line break formats
        StringBuilder buf = new StringBuilder();
        buf.append("a1,b1,c1\r\n");
        buf.append("a2,b2,c2\n");
        buf.append("a3,b3,c3\r");
        buf.append("a4,b4,c4");

        StringReader reader = new StringReader(buf.toString());

        CSVSerializer serializer = new CSVSerializer();
        serializer.setKeys("A", "B", "C");
        serializer.getCSVSerializerListeners().add(new CSVSerializerListener() {
            public void beginList(CSVSerializer csvSerializer, List<?> list) {
                System.out.println("Begin list: " + list);
            }

            public void endList(CSVSerializer csvSerializer) {
                System.out.println("End list");
            }

            public void readItem(CSVSerializer csvSerializer, Object item) {
                System.out.println("Read item: " + item);
            }
        });

        List<?> result = serializer.readObject(reader);

        Map<String, Object> row;

        // Test the first row
        row = (Map<String, Object>)result.get(0);
        assertEquals(row.get("A"), "a1");
        assertEquals(row.get("B"), "b1");
        assertEquals(row.get("C"), "c1");

        // Test the second row
        row = (Map<String, Object>)result.get(1);
        assertEquals(row.get("A"), "a2");
        assertEquals(row.get("B"), "b2");
        assertEquals(row.get("C"), "c2");

        // Test the third row
        row = (Map<String, Object>)result.get(2);
        assertEquals(row.get("A"), "a3");
        assertEquals(row.get("B"), "b3");
        assertEquals(row.get("C"), "c3");

        // Test the fourth row
        row = (Map<String, Object>)result.get(3);
        assertEquals(row.get("A"), "a4");
        assertEquals(row.get("B"), "b4");
        assertEquals(row.get("C"), "c4");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQuotedCommaReadObject() throws IOException, SerializationException {
        StringBuilder buf = new StringBuilder();
        buf.append("a,\",b,\",c\r\n");

        StringReader reader = new StringReader(buf.toString());

        CSVSerializer serializer = new CSVSerializer();
        serializer.setKeys("A", "B", "C");

        List<?> result = serializer.readObject(reader);

        Map<String, Object> row = (Map<String, Object>)result.get(0);
        assertEquals("a", row.get("A"));
        assertEquals(",b,", row.get("B"));
        assertEquals("c", row.get("C"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQuotedQuoteReadObject() throws IOException, SerializationException {
        StringBuilder buf = new StringBuilder();
        buf.append("a,\"\"\"b\"\"\",c\r\n");

        StringReader reader = new StringReader(buf.toString());

        CSVSerializer serializer = new CSVSerializer();
        serializer.setKeys("A", "B", "C");

        List<?> result = serializer.readObject(reader);

        Map<String, Object> row = (Map<String, Object>)result.get(0);
        assertEquals("a", row.get("A"));
        assertEquals("\"b\"", row.get("B"));
        assertEquals("c", row.get("C"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQuotedNewlineReadObject() throws IOException, SerializationException {
        StringBuilder buf = new StringBuilder();
        buf.append("a,\"b\nb  \",c\r\n");

        StringReader reader = new StringReader(buf.toString());

        CSVSerializer serializer = new CSVSerializer();
        serializer.setKeys("A", "B", "C");

        List<?> result = serializer.readObject(reader);

        Map<String, Object> row = (Map<String, Object>)result.get(0);
        assertEquals("a", row.get("A"));
        assertEquals("b\nb", row.get("B"));
        assertEquals("c", row.get("C"));
    }

    @Test
    public void testBasicWriteObject() throws IOException {
        List<Object> items = new ArrayList<Object>();

        HashMap<String, Object> item1 = new HashMap<String, Object>();
        item1.put("A", "a1");
        item1.put("B", "b1");
        item1.put("C", "c1");
        items.add(item1);

        HashMap<String, Object> item2 = new HashMap<String, Object>();
        item2.put("A", "a2");
        item2.put("B", "b2");
        item2.put("C", "c2");
        items.add(item2);

        StringWriter writer = new StringWriter();

        CSVSerializer serializer = new CSVSerializer();
        serializer.setKeys("A", "B", "C");

        serializer.writeObject(items, writer);

        assertEquals("a1,b1,c1\r\na2,b2,c2\r\n", writer.toString());
    }

    @Test
    public void testQuotedCommaWriteObject() throws IOException {
        List<Object> items = new ArrayList<Object>();
        HashMap<String, Object> item = new HashMap<String, Object>();
        item.put("A", "a");
        item.put("B", ",b,");
        item.put("C", "c");
        items.add(item);

        StringWriter writer = new StringWriter();

        CSVSerializer serializer = new CSVSerializer();
        serializer.setKeys("A", "B", "C");

        serializer.writeObject(items, writer);

        assertEquals("a,\",b,\",c\r\n", writer.toString());
    }

    @Test
    public void testQuotedQuoteWriteObject() throws IOException {
        List<Object> items = new ArrayList<Object>();
        HashMap<String, Object> item = new HashMap<String, Object>();
        item.put("A", "a");
        item.put("B", "\"b\"");
        item.put("C", "c");
        items.add(item);

        StringWriter writer = new StringWriter();

        CSVSerializer serializer = new CSVSerializer();
        serializer.setKeys("A", "B", "C");

        serializer.writeObject(items, writer);

        assertEquals("a,\"\"\"b\"\"\",c\r\n", writer.toString());
    }

    @Test
    public void testQuotedNewlineWriteObject() throws IOException {
        List<Object> items = new ArrayList<Object>();
        HashMap<String, Object> item = new HashMap<String, Object>();
        item.put("A", "a");
        item.put("B", "\nb\n");
        item.put("C", "c");
        items.add(item);

        StringWriter writer = new StringWriter();

        CSVSerializer serializer = new CSVSerializer();
        serializer.setKeys("A", "B", "C");

        serializer.writeObject(items, writer);

        assertEquals("a,\"\nb\n\",c\r\n", writer.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInlineKeys() throws IOException, SerializationException {
        StringBuilder buf = new StringBuilder();
        buf.append("A \t, B ,C \n");
        buf.append("a1,b1,c1\n");

        StringReader reader = new StringReader(buf.toString());

        CSVSerializer serializer = new CSVSerializer();
        List<?> result = serializer.readObject(reader);
        Map<String, Object> row = (Map<String, Object>)result.get(0);
        assertEquals(row.get("A"), "a1");
        assertEquals(row.get("B"), "b1");
        assertEquals(row.get("C"), "c1");
    }
}