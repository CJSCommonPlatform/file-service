package uk.gov.justice.services.fileservice.integration;


import static java.util.UUID.randomUUID;
import static javax.json.Json.createReader;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import uk.gov.justice.services.fileservice.datasource.TestDataSourceProvider;
import uk.gov.justice.services.fileservice.repository.ContentJdbcRepository;
import uk.gov.justice.services.fileservice.repository.MetadataJdbcRepository;
import uk.gov.justice.services.fileservice.repository.json.StringJsonSetter;
import uk.gov.justice.services.fileservice.repository.json.JsonSetter;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObject;

import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FilePersistenceIntegrationTest {

    private static final String LIQUIBASE_FILE_STORE_DB_CHANGELOG_XML = "liquibase/file-service-liquibase-db-changelog.xml";

    private static final String URL = "jdbc:h2:mem:test;MV_STORE=FALSE;MVCC=FALSE";
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "sa";
    private static final String DRIVER_CLASS = org.h2.Driver.class.getName();

    private final JsonSetter jsonSetter = new StringJsonSetter();

    private final MetadataJdbcRepository metadataJdbcRepository = new MetadataJdbcRepository(jsonSetter);
    private final ContentJdbcRepository contentJdbcRepository = new ContentJdbcRepository();

    private final TestDataSourceProvider dataSourceProvider = new TestDataSourceProvider(
            URL,
            USERNAME,
            PASSWORD,
            DRIVER_CLASS);

    private Connection connection;


    @Before
    public void setupDatabase() throws Exception {

        connection = dataSourceProvider.getDataSource().getConnection();

        final Liquibase liquibase = new Liquibase(
                LIQUIBASE_FILE_STORE_DB_CHANGELOG_XML,
                new ClassLoaderResourceAccessor(),
                new JdbcConnection(connection));
        liquibase.dropAll();
        liquibase.update("");
    }

    @After
    public void closeConnection() throws SQLException {

        if(connection != null) {
            connection.close();
        }
    }

    @Test
    public void shouldStoreAndRetrieveFileData() throws Exception {

        final UUID fileId = randomUUID();
        final byte[] content = "file-name".getBytes();
        contentJdbcRepository.insert(fileId, content, connection);

        final Optional<byte[]> fileContents = contentJdbcRepository.findByFileId(fileId, connection);

        assertThat(fileContents.isPresent(), is(true));
        assertThat(fileContents.get(), is(content));
    }

    @Test
    public void shouldStoreAndRetrieveMetadata() throws Exception {

        final UUID fileId = randomUUID();

        final String json = "{\"some\": \"json\"}";
        final byte[] content = "some file or other".getBytes();
        final JsonObject metadata = toJsonObject(json);

        contentJdbcRepository.insert(fileId, content, connection);
        metadataJdbcRepository.insert(fileId, metadata, connection);

        final Optional<JsonObject> foundMetadata = metadataJdbcRepository.findByFileId(fileId, connection);

        assertThat(foundMetadata.isPresent(), is(true));
        assertThat(foundMetadata.get(), is(metadata));
    }

    private JsonObject toJsonObject(final String json) {
        return createReader(new StringReader(json)).readObject();
    }
}