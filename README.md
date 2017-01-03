# File Service [![Build Status](https://travis-ci.org/CJSCommonPlatform/file-service.svg?branch=master)](https://github.com/CJSCommonPlatform/file-service)

A simple drop in library for storing binary files in Postgres. This library assumes that it is running inside a JEE container with a datasource configured to point at Postgres

### Maven

    <dependency>
        <groupId>uk.gov.justice.services</groupId>
        <artifactId>file-service-persistence</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    
### To Use

    final JsonObject metadata = ...
    final InputStream fileContentStream = ...

    final UUID fileId = fileStorer.store(metadata, fileContentStream);

    final Optional<FileReference> fileReference = fileRetriever.retrieve(fileId);
    if(fileReference.isPresent()) {
    
        // always remember to close the input streams
        // as this will also close the database Connection
        try(final InputStream inputStream = fileReference.get().getContentStream()) {
        
            // do stuff...
        }
    }

### Database and JNDI setup

There needs to be a Postgres DataSource configured in your container with the JNDI name
    
    java:/app/fileservice/DS.fileservice

There is a liquibase script configured to run against a local database. This database should be created as follows:

    database name:		fileservice
    username:			fileservice
    password:			fileservice
    port:				5432

To run liquibase there is a bash script in the root folder which will create the tables for you. To run:

    ./runLiquibase.sh    

### Issues

*   This implementation is currently Postgres specific: to take advantage of Postgres' *JsonB* data type.
*   The integration tests use H2 as an in memory database.
*   This implementation currently stores the binary file data in Postgres using it's *bytea* data type. Although bytea will allow storage of up to 1 Gb of data, it isn't recommended to store anything larger than 100 Mb as larger files will unreasonably slow down the database.
* 	There is an issue with returning the InputStream of the binary content stored in the database. Although closing the SQL Connection before the InputStream is read and closed doesn't cause any issues by itself, using a pooled Connection can cause problems. If the Connection is given to a new request then the InputStream would be null and the previous request would fail. For this reason, we wrap the InputStream in our own InputStream that contains a reference to the database Connection used to read the file. When **close()** is called on the InputStream then the corresponding SQL Connection is also closed. For this reason it is vitally important to remember to close the InputStream in FileReference after use.
