package dev.abstratium.core.filter;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;

/**
 * Test-only JAX-RS resource that throws exceptions with Hibernate
 * ConstraintViolationException causes, used to verify that
 * DuplicateEntryExceptionMapper walks the exception chain correctly.
 */
@Path("/api/test/duplicate-entry")
public class DuplicateEntryTestResource {

    @GET
    @Path("/wrapped-mysql-duplicate")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerWrappedMySqlDuplicate() {
        SQLException sqlException = new SQLException(
            "Duplicate entry 'x' for key 'T_test.UQ_test_name'", "23000", 1062);
        ConstraintViolationException cve = new ConstraintViolationException(
            "could not execute statement", sqlException, "UQ_test_name");
        throw new RuntimeException("transaction rolled back", cve);
    }

    @GET
    @Path("/wrapped-h2-unique")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerWrappedH2Unique() {
        SQLException sqlException = new SQLException(
            "Unique index or primary key violation: \"UQ_TEST_NAME ON T_TEST(NAME)\"", "23505", 23505);
        ConstraintViolationException cve = new ConstraintViolationException(
            "could not execute statement", sqlException, "UQ_test_name");
        throw new RuntimeException("transaction rolled back", cve);
    }

    @GET
    @Path("/wrapped-fk-violation")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerWrappedFkViolation() {
        SQLException sqlException = new SQLException(
            "Cannot add or update a child row: a foreign key constraint fails", "23000", 1452);
        ConstraintViolationException cve = new ConstraintViolationException(
            "could not execute statement", sqlException, "FK_some_fk");
        throw new RuntimeException("transaction rolled back", cve);
    }

    @GET
    @Path("/plain-exception")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerPlainException() {
        throw new RuntimeException("something went wrong");
    }

    @GET
    @Path("/wrapped-null-message")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerWrappedNullMessage() {
        SQLException sqlException = new SQLException(
            "Duplicate entry 'x' for key 'T_test.UQ_test_name'", "23000", 1062);
        ConstraintViolationException cve = new ConstraintViolationException(null, sqlException, "UQ_test_name");
        throw new RuntimeException("transaction rolled back", cve);
    }

    @GET
    @Path("/h2-unique-null-constraint")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerH2UniqueWithNullConstraint() {
        SQLException sqlException = new SQLException(
            "Unique index or primary key violation: \"ON T_TEST(NAME)\"", "23505", 23505);
        ConstraintViolationException cve = new ConstraintViolationException(
            "Unique index or primary key violation", sqlException, null);
        throw new RuntimeException("transaction rolled back", cve);
    }

    @GET
    @Path("/uq-constraint-name-violation")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerUqConstraintNameViolation() {
        ConstraintViolationException cve = new ConstraintViolationException(
            "could not execute statement", null, "UQ_product_code");
        throw new RuntimeException("transaction rolled back", cve);
    }
}
