package uk.gov.justice.services.fileservice.it;

import static java.io.File.createTempFile;
import static java.nio.file.Files.copy;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

import uk.gov.justice.services.fileservice.api.FileRetriever;
import uk.gov.justice.services.fileservice.api.FileServiceException;
import uk.gov.justice.services.fileservice.api.FileStorer;
import uk.gov.justice.services.fileservice.client.FileService;
import uk.gov.justice.services.fileservice.domain.FileReference;
import uk.gov.justice.services.fileservice.it.helpers.IntegrationTestDataSourceProvider;
import uk.gov.justice.services.fileservice.it.helpers.LiquibaseDatabaseBootstrapper;
import uk.gov.justice.services.fileservice.json.StringJsonSetter;
import uk.gov.justice.services.fileservice.repository.ContentJdbcRepository;
import uk.gov.justice.services.fileservice.repository.FileStore;
import uk.gov.justice.services.fileservice.repository.MetadataJdbcRepository;
import uk.gov.justice.services.jdbc.persistence.InitialContextFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.UUID;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.sql.DataSource;

import org.apache.openejb.jee.Application;
import org.apache.openejb.jee.WebApp;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Module;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ApplicationComposer.class)
public class FileServiceIT {

    private static final String LIQUIBASE_FILE_STORE_DB_CHANGELOG_XML = "liquibase/file-service-liquibase-db-changelog.xml";

    private final LiquibaseDatabaseBootstrapper liquibaseDatabaseBootstrapper = new LiquibaseDatabaseBootstrapper();

    @Resource(name = "openejb/Resource/DS.fileservice")
    private DataSource dataSource;

    @Inject
    FileService fileService;

    @Module
    @Classes(cdi = true, value = {

            FileRetriever.class,
            FileServiceException.class,
            FileStorer.class,
            FileReference.class,

            InitialContextFactory.class,

            FileService.class,

            IntegrationTestDataSourceProvider.class,

            StringJsonSetter.class,
            ContentJdbcRepository.class,
            FileStore.class,
            MetadataJdbcRepository.class
    })
    public WebApp war() {
        return new WebApp()
                .contextRoot("core-test")
                .addServlet("TestApp", Application.class.getName());
    }

    @Before
    public void initDatabase() throws Exception {
        liquibaseDatabaseBootstrapper.bootstrap(
                LIQUIBASE_FILE_STORE_DB_CHANGELOG_XML,
                dataSource.getConnection());
    }

    @Test
    public void shouldSuccessfullyStoreABinaryFile() throws Exception {

        assertThat(fileService, is(notNullValue()));

        final JsonObject metadata = createObjectBuilder()
                .add("Test", "test")
                .build();

        final File inputFile = getFileFromClasspath();
        final FileInputStream inputStream = new FileInputStream(inputFile);

        final UUID fileId = fileService.store(metadata, inputStream);

        inputStream.close();

        final FileReference fileReference = fileService
                .retrieve(fileId)
                .orElseThrow(() -> new AssertionError("Failed to get FileReference from File Store"));

        assertThat(fileReference.getMetadata(), is(metadata));

        final InputStream contentStream = fileReference.getContentStream();

        final File outputFile = createTempFile("created-for-testing-file-store-please-delete-me_2", "jpg");
        outputFile.deleteOnExit();

        copy(contentStream, outputFile.toPath(), REPLACE_EXISTING);

        contentStream.close();

        assertThat(outputFile.exists(), is(true));
        assertThat(outputFile.length(), is(greaterThan(0L)));
        assertThat(outputFile.length(), is(inputFile.length()));
    }

    public File getFileFromClasspath() throws URISyntaxException {
        final URL url = getClass().getResource("/for-testing-file-store.jpg");
        return new File(url.toURI());
    }
}
