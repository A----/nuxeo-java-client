/*
 * (C) Copyright 2016-2017 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *         Vladimir Pasquier <vpasquier@nuxeo.com>
 *         Mincong Huang <mhuang@nuxeo.com>
 */
package org.nuxeo.client.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.client.api.objects.Document;
import org.nuxeo.client.api.objects.Documents;
import org.nuxeo.client.api.objects.RecordSet;
import org.nuxeo.client.api.objects.acl.ACE;
import org.nuxeo.client.api.objects.acl.ACP;
import org.nuxeo.client.api.objects.audit.Audit;
import org.nuxeo.client.api.objects.blob.Blob;
import org.nuxeo.client.api.objects.blob.Blobs;
import org.nuxeo.client.internals.spi.NuxeoClientException;
import org.nuxeo.client.test.marshallers.DocumentMarshaller;
import org.nuxeo.client.test.objects.DataSet;
import org.nuxeo.client.test.objects.Field;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.ecm.restapi.test.RestServerInit;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.Jetty;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 0.1
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, AuditFeature.class })
@Jetty(port = 18090)
@Deploy({ "org.nuxeo.ecm.core.io", "org.nuxeo.ecm.permissions" })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
public class TestRepository extends TestBase {

    @Before
    public void authentication() {
        login();
    }

    @Test
    public void itCanFetchRoot() {
        Document root = nuxeoClient.repository().fetchDocumentRoot();
        assertNotNull(root);
        assertEquals("Root", root.getType());
        assertEquals("document", root.getEntityType());
        assertEquals("/", root.getParentRef());
        assertEquals("/", root.getPath());
    }

    @Test
    public void itCanFetchRootWithRepositoryName() {
        Document root = nuxeoClient.repository().fetchDocumentRoot();
        root = nuxeoClient.repository().repositoryName(root.getRepositoryName()).fetchDocumentRoot();
        assertNotNull(root);
        assertEquals("Root", root.getType());
        assertEquals("document", root.getEntityType());
        assertEquals("/", root.getParentRef());
        assertEquals("/", root.getPath());
    }

    @Test
    public void itCanFetchFolder() {
        Document root = nuxeoClient.repository().fetchDocumentRoot();
        Document folder = nuxeoClient.repository().fetchDocumentByPath
                ("/folder_2");
        assertNotNull(folder);
        assertEquals("Folder", folder.getType());
        assertEquals("document", folder.getEntityType());
        assertEquals(root.getUid(), folder.getParentRef());
        assertEquals("/folder_2", folder.getPath());
        assertEquals("Folder 2", folder.getTitle());
    }

    @Test
    public void itCanFetchFolderWithRepositoryName() {
        Document root = nuxeoClient.repository().fetchDocumentRoot();
        Document folder = nuxeoClient.repository()
                                     .repositoryName(root.getRepositoryName())
                                     .fetchDocumentByPath("/folder_2");
        assertNotNull(folder);
        assertEquals("Folder", folder.getType());
        assertEquals("document", folder.getEntityType());
        assertEquals(root.getUid(), folder.getParentRef());
        assertEquals("/folder_2", folder.getPath());
        assertEquals("Folder 2", folder.getTitle());
    }

    @Test
    public void itCanFetchNote() {

        Document note = nuxeoClient.repository().fetchDocumentByPath("/folder_1/note_1");
        assertNotNull(note);
        assertEquals("Note", note.getType());
        assertEquals("document", note.getEntityType());

        assertEquals("/folder_1/note_1", note.getPath());
        assertEquals("Note 1", note.getTitle());
    }

    @Test
    public void itCanCreateDocument() {
        Document folder = nuxeoClient.repository().fetchDocumentByPath("/folder_1");
        Document document = new Document("file", "File");
        document.setPropertyValue("dc:title", "new title");
        document = nuxeoClient.repository().createDocumentByPath("/folder_1", document);
        assertNotNull(document);
        assertEquals("File", document.getType());
        assertEquals("document", document.getEntityType());
        assertEquals(folder.getUid(), document.getParentRef());
        assertEquals("/folder_1/file", document.getPath());
        assertEquals("new title", document.getTitle());
        assertEquals("new title", document.get("dc:title"));
    }

    @Test
    public void itCanQuery() {
        Documents documents = nuxeoClient.repository().query("SELECT * From Note");
        assertTrue(documents.getDocuments().size() != 0);
        Document document = documents.getDocuments().get(0);
        assertEquals("Note", document.getType());
        assertEquals("test", document.getRepositoryName());
        assertEquals("project", document.getState());
    }

    @Test
    public void itCanUseCaching() {
        // Retrieve a document from query
        Document document = nuxeoClient.enableDefaultCache().repository().fetchDocumentByPath("/folder_1/note_3");
        assertEquals("Note 3", document.get("dc:title"));
        assertTrue(nuxeoClient.getNuxeoCache().size() == 1);

        // Update this document
        Document documentUpdated = new Document("test update", "Note");
        documentUpdated.setId(document.getId());
        documentUpdated.setPropertyValue("dc:title", "note updated");
        documentUpdated = nuxeoClient.repository().updateDocument(documentUpdated);
        assertEquals("note updated", documentUpdated.get("dc:title"));

        // Retrieve again this document within cache
        document = nuxeoClient.repository().fetchDocumentByPath("/folder_1/note_3");
        assertEquals("Note 3", document.get("dc:title"));
        assertTrue(nuxeoClient.getNuxeoCache().size() == 2);

        // Refresh the cache and check the update has been recovered.
        document = nuxeoClient.repository().refreshCache().fetchDocumentByPath("/folder_1/note_3");
        assertEquals("note updated", document.get("dc:title"));
        assertTrue(nuxeoClient.getNuxeoCache().size() == 1);
    }

    @Test
    public void itCanUpdateDocument() {
        Document document = nuxeoClient.repository().fetchDocumentByPath("/folder_1/note_0");
        assertEquals("Note", document.getType());
        assertEquals("test", document.getRepositoryName());
        assertEquals("project", document.getState());
        assertEquals("Note 0", document.getTitle());
        assertEquals("Note 0", document.get("dc:title"));

        Document documentUpdated = new Document("test update", "Note");
        documentUpdated.setId(document.getId());
        documentUpdated.setPropertyValue("dc:title", "note updated");
        documentUpdated.setTitle("note updated");
        documentUpdated.setPropertyValue("dc:nature", "test");

        documentUpdated = nuxeoClient.repository().updateDocument(documentUpdated);
        assertNotNull(documentUpdated);
        assertEquals("note updated", documentUpdated.get("dc:title"));
        assertEquals("test", documentUpdated.get("dc:nature"));

        // Check if the document in the repository has been changed.
        Document result = nuxeoClient.repository().fetchDocumentById(documentUpdated.getId());
        assertNotNull(result);
        assertEquals("note updated", result.get("dc:title"));
        assertEquals("test", result.get("dc:nature"));
    }

    @Test
    public void itCanDeleteDocument() {
        Document documentToDelete = nuxeoClient.repository().fetchDocumentByPath("/folder_1/note_1");
        assertNotNull(documentToDelete);
        assertTrue(session.exists(new IdRef(documentToDelete.getId())));
        nuxeoClient.repository().deleteDocument(documentToDelete);
        fetchInvalidations();
        assertTrue(!session.exists(new IdRef(documentToDelete.getId())));
        Document documentToDelete3 = nuxeoClient.repository().fetchDocumentByPath("/folder_1/note_3");
        assertNotNull(documentToDelete3);
        assertTrue(session.exists(new IdRef(documentToDelete3.getId())));
        nuxeoClient.repository().deleteDocument(documentToDelete3.getId());
        fetchInvalidations();
        assertTrue(!session.exists(new IdRef(documentToDelete3.getId())));
    }

    @Test
    public void itCanUseCustomMarshallers() {

        Document folder = nuxeoClient.registerMarshaller(new DocumentMarshaller())
                                     .repository()
                                     .fetchDocumentByPath("/folder_1");
        assertNotNull(folder);
        assertEquals(folder.getPath(), "/folder_1");
        assertEquals(folder.getState(), "project");
        assertEquals(folder.getType(), "Folder");

        nuxeoClient.clearMarshaller();
    }

    @Test
    public void itCanUseQueriesAndResultSet() {
        RecordSet documents = (RecordSet) nuxeoClient.automation()
                                                     .param("query", "SELECT * FROM Document")
                                                     .execute("Repository.ResultSetQuery");
        assertTrue(documents.getUuids().size() != 0);
    }

    @Test
    public void itCanFail() {
        try {
            nuxeoClient.repository().fetchDocumentByPath("/folder_1/wrong");
            fail("Should be not found");
        } catch (NuxeoClientException reason) {
            assertEquals(404, reason.getStatus());
        }
    }

    @Test
    public void itCanFetchBlob() {
        Document file = nuxeoClient.repository().fetchDocumentByPath("/folder_2/file");
        Blob blob = file.fetchBlob();
        assertNotNull(blob);
    }

    @Test
    public void itCanFetchBlobByPath() {
        Document file = nuxeoClient.repository().fetchDocumentByPath("/folder_2/file");

        // Attach a blob
        File temp1 = FileUtils.getResourceFileFromContext("sample.jpg");
        File temp2 = FileUtils.getResourceFileFromContext("sample.jpg");
        Blobs inputBlobs = new Blobs();
        inputBlobs.add(temp1);
        inputBlobs.add(temp2);
        Blobs blobs = nuxeoClient.automation()
                                 .newRequest("Blob.AttachOnDocument")
                                 .param("document", file.getPath())
                                 .param("xpath", "files:files")
                                 .input(inputBlobs)
                                 .execute();
        assertNotNull(blobs);
        assertEquals("sample.jpg", blobs.getBlobs().get(0).getFileName());
        assertEquals("sample.jpg", blobs.getBlobs().get(1).getFileName());

        //Fetch blob by path
        Blob blob = nuxeoClient.repository().fetchBlobByPath(file.getPath(), "files:files/0/file");
        System.out.println(file.getProperties());
        assertNotNull(blob);
    }

    @Test
    public void itCanFetchBlobById() {
        Document file = nuxeoClient.repository().fetchDocumentByPath("/folder_2/file");

        // Attach a blob
        File temp1 = FileUtils.getResourceFileFromContext("sample.jpg");
        File temp2 = FileUtils.getResourceFileFromContext("sample.jpg");
        Blobs inputBlobs = new Blobs();
        inputBlobs.add(temp1);
        inputBlobs.add(temp2);
        Blobs blobs = nuxeoClient.automation()
                                 .newRequest("Blob.AttachOnDocument")
                                 .param("document", file.getPath())
                                 .param("xpath", "files:files")
                                 .input(inputBlobs)
                                 .execute();
        assertNotNull(blobs);
        assertEquals("sample.jpg", blobs.getBlobs().get(0).getFileName());
        assertEquals("sample.jpg", blobs.getBlobs().get(1).getFileName());

        //Fetch blob by id
        Blob blob = nuxeoClient.repository().fetchBlobById(file.getUid(),"files:files/0/file");
        System.out.println(file.getProperties());
        assertNotNull(blob);
    }

    @Test
    public void itCanFetchChildren() {
        Document folder = nuxeoClient.repository().fetchDocumentByPath("/folder_2");
        Documents children = folder.fetchChildren();
        assertTrue(children.size() != 0);
    }

    @Test
    public void itCanPlayWithChildren() {
        Document folder = nuxeoClient.repository().fetchDocumentByPath("/folder_2");
        Documents children = folder.fetchChildren();
        assertTrue(children.size() != 0);
        children = children.getDocument(0).fetchChildren();
        assertNotNull(children);
    }

    @Test
    public void itCanFetchACP() {
        Document folder = nuxeoClient.repository().fetchDocumentByPath("/folder_2");
        ACP acp = folder.fetchPermissions();
        assertTrue(acp.getAcls().size() != 0);
        assertEquals("inherited", acp.getAcls().get(0).getName());
        assertEquals("Administrator", acp.getAcls().get(0).getAces().get(0).getUsername());
    }

    @Test
    public void itCanManagePermissions() {
        // ** CREATION **/
        // First Check
        Document folder = nuxeoClient.repository().fetchDocumentByPath("/folder_2");
        ACP acp = folder.fetchPermissions();
        assertTrue(acp.getAcls().size() != 0);
        assertEquals(1, acp.getAcls().size());
        assertEquals(2, acp.getAcls().get(0).getAces().size());
        assertEquals("inherited", acp.getAcls().get(0).getName());
        // Settings
        GregorianCalendar begin = new GregorianCalendar(2015, Calendar.JUNE, 20, 12, 34, 56);
        GregorianCalendar end = new GregorianCalendar(2015, Calendar.JULY, 14, 12, 34, 56);
        ACE ace = new ACE();
        ace.setUsername("user0");
        ace.setPermission("Write");
        ace.setCreator("Administrator");
        ace.setBegin(begin);
        ace.setEnd(end);
        ace.setBlockInheritance(true);
        folder.addPermission(ace);
        // Final Check
        folder = nuxeoClient.repository().fetchDocumentByPath("/folder_2");
        acp = folder.fetchPermissions();
        assertTrue(acp.getAcls().size() != 0);
        assertEquals(1, acp.getAcls().size());
        assertEquals(4, acp.getAcls().get(0).getAces().size());
        assertEquals("local", acp.getAcls().get(0).getName());
        // ** DELETION **/
        folder.removePermission("user0");
        // Final Check
        folder = nuxeoClient.repository().fetchDocumentByPath("/folder_2");
        acp = folder.fetchPermissions();
        assertTrue(acp.getAcls().size() != 0);
        assertEquals(1, acp.getAcls().size());
        assertEquals(3, acp.getAcls().get(0).getAces().size());
        assertEquals("local", acp.getAcls().get(0).getName());
    }

    @Test
    public void itCanFetchAudit() {
        Document root = nuxeoClient.repository().fetchDocumentRoot();
        Audit audit = root.fetchAudit();
        assertTrue(audit.getLogEntries().size() != 0);
        assertEquals("eventDocumentCategory", audit.getLogEntries().get(0).getCategory());
    }

    @Test
    public void testMultiThread() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                RecordSet documents = nuxeoClient.automation()
                                                 .param("query", "SELECT * FROM Document")
                                                 .execute("Repository.ResultSetQuery");
                assertTrue(documents.getUuids().size() != 0);
            } catch (Exception e) {
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                RecordSet documents = nuxeoClient.automation()
                                                 .param("query", "SELECT * FROM Document")
                                                 .execute("Repository.ResultSetQuery");
                assertTrue(documents.getUuids().size() != 0);
            } catch (Exception e) {
            }
        });
        t.start();
        t2.start();
        t.join();
        t2.join();
    }

    @Test
    public void itCanFetchDocumentWithCallback() throws InterruptedException {
        nuxeoClient.repository().fetchDocumentRoot(new Callback<Document>() {
            @Override
            public void onResponse(Call<Document> call, Response<Document>
                    response) {
                if (!response.isSuccessful()) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    NuxeoClientException nuxeoClientException;
                    try {
                        nuxeoClientException = objectMapper.readValue
                                (response.errorBody().string(),
                                        NuxeoClientException.class);
                    } catch (IOException reason) {
                        throw new NuxeoClientException(reason);
                    }
                    fail(nuxeoClientException.getRemoteStackTrace());
                }
                Document folder = response.body();
                assertNotNull(folder);
                assertEquals("Folder", folder.getType());
                assertEquals("document", folder.getEntityType());
                assertEquals("/folder_2", folder.getPath());
                assertEquals("Folder 2", folder.getTitle());
            }

            @Override
            public void onFailure(Call<Document> call, Throwable t) {
                fail(t.getMessage());
            }
        });
    }

    @Test
    public void itCanUseEnrichers() {
        Document document = nuxeoClient.enrichers("acls", "breadcrumb").repository().fetchDocumentByPath("/folder_2");
        assertNotNull(document);
        assertTrue(((List) document.getContextParameters().get("acls")).size
                () == 1);
        assertTrue(((Map) document.getContextParameters().get("breadcrumb")).size() == 2);
    }

    @Ignore("NXP-19295 - We don't want to use that use case anymore. But keeping the test for explanation")
    @Test
    public void itCanHandleComplexPropertiesWithJson() throws IOException {
        // DataSet doctype comes from nuxeo-automation-test
        Document folder = nuxeoClient.repository().fetchDocumentByPath("folder_1");
        Document document = new Document("file", "DataSet");
        document.setPropertyValue("dc:title", "new title");

        File fieldAsJsonFile = FileUtils.getResourceFileFromContext("blob.json");
        assertNotNull(fieldAsJsonFile);
        String fieldsDataAsJSon = FileUtils.readFile(fieldAsJsonFile);
        fieldsDataAsJSon = fieldsDataAsJSon.replaceAll("\n", "");
        fieldsDataAsJSon = fieldsDataAsJSon.replaceAll("\r", "");
        Map<String, Object> creationProps = new HashMap<>();
        creationProps.put("ds:tableName", "MyTable");
        creationProps.put("ds:fields", fieldsDataAsJSon);
        document.setProperties(creationProps);

        document = nuxeoClient.repository().createDocumentByPath("folder_1", document);
        assertNotNull(document);
        assertEquals("DataSet", document.getType());
        List list = (List) document.getProperties().get("ds:fields");
        assertFalse(list.isEmpty());
        assertEquals(5, list.size());
        assertEquals("document", document.getEntityType());
        assertEquals(folder.getUid(), document.getParentRef());
        assertEquals("/folder_1/file", document.getPath());
        assertEquals("file", document.getTitle());
    }

    @Test
    public void itCanHandleGregorianCalendarUTC() {
        GregorianCalendar calendar = new GregorianCalendar(2017, Calendar.MAY, 4, 3, 2, 1);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String calendarStr = formatter.format(calendar.getTime());
        assertEquals("2017-05-04T03:02:01.000Z", calendarStr);

        Document file = new Document("My Title", "File");
        file.set("dc:issued", calendarStr);
        file = nuxeoClient.repository().createDocumentByPath("/", file);
        assertEquals("2017-05-04T03:02:01.000Z", file.getPropertyValue("dc:issued"));

        calendar.add(Calendar.MONTH, 1);
        calendarStr = formatter.format(calendar.getTime());
        assertEquals("2017-06-04T03:02:01.000Z", calendarStr);
        file.set("dc:issued", calendarStr);
        file = nuxeoClient.repository().updateDocument(file);
        assertEquals("2017-06-04T03:02:01.000Z", file.getPropertyValue("dc:issued"));
    }

    @Test
    public void itCanHandleGregorianCalendarCET() {
        GregorianCalendar calendar = new GregorianCalendar(2017, Calendar.MAY, 4, 3, 2, 1);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        calendar.setTimeZone(TimeZone.getTimeZone("CET"));
        formatter.setTimeZone(TimeZone.getTimeZone("CET"));
        String calendarStr = formatter.format(calendar.getTime());
        assertEquals("2017-05-04T03:02:01.000+02:00", calendarStr);

        Document file = new Document("My Title", "File");
        file.set("dc:issued", calendarStr);
        file = nuxeoClient.repository().createDocumentByPath("/", file);
        assertEquals("2017-05-04T01:02:01.000Z", file.getPropertyValue("dc:issued"));

        calendar.add(Calendar.MONTH, 1);
        calendarStr = formatter.format(calendar.getTime());
        assertEquals("2017-06-04T03:02:01.000+02:00", calendarStr);
        file.set("dc:issued", calendarStr);
        file = nuxeoClient.repository().updateDocument(file);
        assertEquals("2017-06-04T01:02:01.000Z", file.getPropertyValue("dc:issued"));
    }

    @Test
    public void itCanHandleZonedDateTimeUTC() {
        ZonedDateTime dateTime = LocalDate.of(2017, Month.MAY, 4).atTime(3, 2, 1).atZone(ZoneId.of("UTC"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        String dateTimeStr = dateTime.format(formatter);
        assertEquals("2017-05-04T03:02:01.000Z", dateTimeStr);

        Document file = new Document("My Title", "File");
        file.set("dc:issued", dateTimeStr);
        file = nuxeoClient.repository().createDocumentByPath("/", file);
        assertEquals("File", file.getType());
        assertEquals("2017-05-04T03:02:01.000Z", file.getPropertyValue("dc:issued"));

        dateTime = dateTime.plus(1, ChronoUnit.MONTHS);
        file.set("dc:issued", dateTime.format(formatter));
        file = nuxeoClient.repository().updateDocument(file);
        assertEquals("2017-06-04T03:02:01.000Z", file.getPropertyValue("dc:issued"));
    }

    @Test
    public void itCanHandleZonedDateTimeCET() {
        ZonedDateTime dateTime = LocalDate.of(2017, Month.MAY, 4).atTime(3, 2, 1).atZone(ZoneId.of("CET"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        String dateTimeStr = dateTime.format(formatter);
        assertEquals("2017-05-04T03:02:01.000+02:00", dateTimeStr);

        Document file = new Document("My Title", "File");
        file.set("dc:issued", dateTimeStr);
        file = nuxeoClient.repository().createDocumentByPath("/", file);
        assertEquals("File", file.getType());
        assertEquals("2017-05-04T01:02:01.000Z", file.getPropertyValue("dc:issued"));

        dateTime = dateTime.plus(1, ChronoUnit.MONTHS);
        file.set("dc:issued", dateTime.format(formatter));
        file = nuxeoClient.repository().updateDocument(file);
        assertEquals("2017-06-04T01:02:01.000Z", file.getPropertyValue("dc:issued"));
    }

    /**
     * Dates can only be handled as {@link java.lang.String}. Otherwise, exception should be raised. There're several
     * examples showing how to convert date correctly into ISO 8601 format:
     * <ul>
     * <li>Convert {@link java.util.GregorianCalendar} in UTC, see {@link #itCanHandleGregorianCalendarUTC}.
     * <li>Convert {@link java.util.GregorianCalendar} in CET, see {@link #itCanHandleGregorianCalendarCET}.
     * <li>Convert {@link java.time.ZonedDateTime} in UTC, see {@link #itCanHandleZonedDateTimeCET}.
     * <li>Convert {@link java.time.ZonedDateTime} in CET, see {@link #itCanHandleZonedDateTimeCET}.
     * </ul>
     */
    @Test
    public void itCannotHandleDateByDefault() {
        Document file = new Document("My Title", "File");
        String expectedMsg1 = buildErrorMsgForDate("dc:issued", GregorianCalendar.class);
        String expectedMsg2 = buildErrorMsgForDate("dc:issued", Date.class);

        GregorianCalendar dateType1 = new GregorianCalendar();
        Date dateType2 = new Date(System.currentTimeMillis());

        assertExceptionFor(doc -> doc.set("dc:issued", dateType1), file, expectedMsg1);
        assertExceptionFor(doc -> doc.set("dc:issued", dateType2), file, expectedMsg2);
    }

    @Test
    public void itCannotHandlePropsIfDateFound() {
        Document file = new Document("My Title", "File");
        Map<String, Object> props = new HashMap<>();
        props.put("dc:issued", new GregorianCalendar());
        String expectedMsg = buildErrorMsgForDate("dc:issued", GregorianCalendar.class);

        assertExceptionFor(doc -> doc.setProperties(props), file, expectedMsg);
        assertExceptionFor(doc -> doc.setDirtyProperties(props), file, expectedMsg);
    }

    @Test
    public void itCannotHandlePropsIfDateArrayFound() {
        Document file = new Document("My Title", "File");
        Map<String, Object> props = new HashMap<>();
        props.put("sth:dateArray", new Object[] { "unused", new GregorianCalendar() });
        String expectedMsg = buildErrorMsgForDate("sth:dateArray", GregorianCalendar.class);

        assertExceptionFor(doc -> doc.setProperties(props), file, expectedMsg);
        assertExceptionFor(doc -> doc.setDirtyProperties(props), file, expectedMsg);
    }

    @Test
    public void itCannotHandlePropsIfDateListFound() {
        Document file = new Document("My Title", "File");
        Map<String, Object> props = new HashMap<>();
        props.put("sth:dateList", Arrays.asList(new GregorianCalendar(), new GregorianCalendar()));
        String expectedMsg = buildErrorMsgForDate("sth:dateList", GregorianCalendar.class);

        assertExceptionFor(doc -> doc.setProperties(props), file, expectedMsg);
        assertExceptionFor(doc -> doc.setDirtyProperties(props), file, expectedMsg);
    }

    @Test
    public void itCannotHandlePropsIfDateFoundInComplexProps() {
        Document file = new Document("My Title", "File");
        Map<String, Object> props = new HashMap<>();
        Map<String, Object> complexProps = new HashMap<>();

        complexProps.put("complex:date", new Date(System.currentTimeMillis()));
        props.put("sth:complex", complexProps);
        String expectedMsg = buildErrorMsgForDate("complex:date", Date.class);

        assertExceptionFor(doc -> doc.setProperties(props), file, expectedMsg);
        assertExceptionFor(doc -> doc.setDirtyProperties(props), file, expectedMsg);
    }

    private void assertExceptionFor(Consumer<Document> consumer, Document doc, String expectedMsg) {
        try {
            consumer.accept(doc);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), expectedMsg, e.getMessage());
        }
    }

    @Test
    public void itCannotConstructDocumentIfDateFoundInProps() {
        Map<String, Object> complexProps = new HashMap<>();
        complexProps.put("complex:date", new Date(System.currentTimeMillis()));

        Map<String, Object> withComplexProps = new HashMap<>();
        withComplexProps.put("sth:complex", complexProps);
        try {
            new Document(null, null, null, null, null, null, null, null, null, null, null, false, withComplexProps,
                    null);
            fail();
        } catch (IllegalArgumentException e) {
            String expectedMsg = buildErrorMsgForDate("complex:date", Date.class);
            assertEquals(e.getMessage(), expectedMsg, e.getMessage());
        }

        Map<String, Object> withoutComplexProps = new HashMap<>();
        withoutComplexProps.put("dc:issued", new GregorianCalendar());
        try {
            new Document(null, null, null, null, null, null, null, null, null, null, null, false, withoutComplexProps,
                    null);
            fail();
        } catch (IllegalArgumentException e) {
            String expectedMsg = buildErrorMsgForDate("dc:issued", GregorianCalendar.class);
            assertEquals(e.getMessage(), expectedMsg, e.getMessage());
        }
    }

    @Test
    public void itCanHandleComplexProperties() throws IOException,
            NoSuchFieldException, IllegalAccessException {
        // DataSet doctype comes from nuxeo-automation-test
        Document folder = nuxeoClient.repository().fetchDocumentByPath("/folder_1");
        Document document = new Document("file", "DataSet");
        document.setPropertyValue("dc:title", "new title");

        List<Field> fields = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        roles.add("BenchmarkIndicator");
        roles.add("Decision");
        Field field1 = new Field("string", "description", roles, "columnName", "sqlTypeHint", "name");
        Field field2 = new Field("string", "description", roles, "columnName", "sqlTypeHint", "name");
        fields.add(field1);
        fields.add(field2);
        Map<String, Object> creationProps = new HashMap<>();
        creationProps.put("ds:tableName", "MyTable");
        creationProps.put("ds:fields", fields);
        document.setProperties(creationProps);

        document = nuxeoClient.repository().createDocumentByPath("/folder_1", document);
        assertNotNull(document);
        assertEquals("DataSet", document.getType());
        List list = (List) document.getProperties().get("ds:fields");
        assertFalse(list.isEmpty());
        assertEquals(2, list.size());
        assertEquals("document", document.getEntityType());
        assertEquals(folder.getUid(), document.getParentRef());
        assertEquals("/folder_1/file", document.getPath());
        assertEquals("file", document.getTitle());

        // Here we are using a sub class DataSet of Document which let the dev implementing business logic.
        fields.clear();
        roles.clear();
        roles.add("BenchmarkIndicator");
        Field field = new Field("string", "description", roles, "columnName", "sqlTypeHint", "name");
        fields.add(field);
        DataSet dataset = new DataSet("file", "DataSet");
        dataset.setId(document.getId());
        dataset.setPropertyValue("ds:fields", fields);

        document = nuxeoClient.repository().updateDocument(dataset);
        dataset = new DataSet(document);
        assertNotNull(dataset);
        fields = (List<Field>) dataset.getProperties().get("ds:fields");
        assertNotNull(fields);
        fields = dataset.getFields();
        assertFalse(fields.isEmpty());
        assertEquals(1, fields.size());
        assertEquals(1, fields.get(0).getRoles().size());
    }

    /**
     * @since 2.3
     */
    @Test
    public void itCanCheckIfDocumentIsProxy() {

        Document root = nuxeoClient.repository().fetchDocumentRoot();
        Document folder = nuxeoClient.repository()
                                     .repositoryName(root.getRepositoryName())
                                     .fetchDocumentByPath("/folder_1");

        assertEquals(folder.isProxy(), false);

        Document proxy = nuxeoClient.automation()
                                    .param("Destination Path", root.getPath())
                                    .input(folder)
                                    .execute("Document.CreateLiveProxy");
        assertEquals(proxy.isProxy(), true);
    }

    private String buildErrorMsgForDate(String key, Class<?> valueType) {
        return String.format(
                "Property '%s' has value of type '%s'. However, date values are not supported in Nuxeo Java Client."
                        + " Please convert it to String with ISO 8601 format \"yyyy-MM-dd'T'HH:mm:ss.SSSXXX\" before"
                        + " setting it as property.",
                key, valueType.getTypeName());
    }
}
