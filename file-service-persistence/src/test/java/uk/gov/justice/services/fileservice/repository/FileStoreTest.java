package uk.gov.justice.services.fileservice.repository;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.file.api.domain.StorableFile;

import java.sql.Connection;
import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


@RunWith(MockitoJUnitRunner.class)
public class FileStoreTest {

    @Mock
    private ContentJdbcRepository contentJdbcRepository;

    @Mock
    private MetadataJdbcRepository metadataJdbcRepository;

    @InjectMocks
    private FileStore fileStore;

    @Test
    public void shouldInsertTheMetadataAndContentIfFileDoesNotYetExistInDatabase() throws Exception {

        final UUID fileId = randomUUID();
        final JsonObject metadata = mock(JsonObject.class);
        final byte[] content = "the file content".getBytes();

        final Connection connection = mock(Connection.class);

        when(contentJdbcRepository.findByFileId(fileId, connection)).thenReturn(empty());

        fileStore.store(fileId, content, metadata, connection);

        final InOrder inOrder = inOrder(
                contentJdbcRepository,
                metadataJdbcRepository);

        inOrder.verify(contentJdbcRepository).findByFileId(fileId, connection);
        inOrder.verify(contentJdbcRepository).insert(fileId, content, connection);
        inOrder.verify(metadataJdbcRepository).insert(fileId, metadata, connection);

        verify(contentJdbcRepository, never()).update(fileId, content, connection);
        verify(metadataJdbcRepository, never()).update(fileId, metadata, connection);
    }

    @Test
    public void shouldUpdateTheMetadataAndContentWhenTheFileExistsInTheDatabase() throws Exception {

        final UUID fileId = randomUUID();
        final JsonObject metadata = mock(JsonObject.class);
        final byte[] content = "the file content".getBytes();

        final Connection connection = mock(Connection.class);

        when(contentJdbcRepository.findByFileId(fileId, connection)).thenReturn(of(content));

        fileStore.store(fileId, content, metadata, connection);

        final InOrder inOrder = inOrder(
                contentJdbcRepository,
                metadataJdbcRepository);

        inOrder.verify(contentJdbcRepository).findByFileId(fileId, connection);
        inOrder.verify(contentJdbcRepository).update(fileId, content, connection);
        inOrder.verify(metadataJdbcRepository).update(fileId, metadata, connection);

        verify(contentJdbcRepository, never()).insert(fileId, content, connection);
        verify(metadataJdbcRepository, never()).insert(fileId, metadata, connection);
    }

    @Test
    public void shouldFindAFileUsingTheFileAndMetadataTables() throws Exception {

        final UUID fileId = randomUUID();
        final byte[] content = "the file content".getBytes();
        final Connection connection = mock(Connection.class);

        final JsonObject metadata = mock(JsonObject.class);

        when(contentJdbcRepository.findByFileId(fileId, connection)).thenReturn(of(content));
        when(metadataJdbcRepository.findByFileId(fileId, connection)).thenReturn(of(metadata));

        final Optional<StorableFile> storableFile = fileStore.find(fileId, connection);

        assertThat(storableFile.isPresent(), is(true));

        assertThat(storableFile.get().getFileId(), is(fileId));
        assertThat(storableFile.get().getContent(), is(content));
        assertThat(storableFile.get().getMetadata(), is(metadata));
    }

    @Test
    public void shouldReturnEmptyIfNoMetadataForAFileExistsInTheDatabase() throws Exception {

        final UUID fileId = randomUUID();
        final Connection connection = mock(Connection.class);

        when(metadataJdbcRepository.findByFileId(fileId, connection)).thenReturn(empty());
        when(contentJdbcRepository.findByFileId(fileId, connection)).thenReturn(empty());

        final Optional<StorableFile> storableFile = fileStore.find(fileId, connection);

        assertThat(storableFile.isPresent(), is(false));
    }

    @Test
    public void shouldThrowAnExceptionIfMetadataCanBeFoundForAFileButNotAnyContent() throws Exception {

        final UUID fileId = randomUUID();
        final Connection connection = mock(Connection.class);

        final JsonObject metadata = mock(JsonObject.class);

        when(metadataJdbcRepository.findByFileId(fileId, connection)).thenReturn(of(metadata));
        when(contentJdbcRepository.findByFileId(fileId, connection)).thenReturn(empty());

        try {
            fileStore.find(fileId, connection);
            fail();
        } catch (final TransactionFailedException expected) {
            assertThat(expected.getMessage(), is("No file content found for file id " + fileId + " but metadata exists for that id"));
        }

    }
}