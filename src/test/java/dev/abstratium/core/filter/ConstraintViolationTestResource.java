package dev.abstratium.core.filter;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;

/**
 * Test-only JAX-RS resource that throws Hibernate ConstraintViolationException
 * with different underlying SQL errors. Used by ConstraintViolationExceptionMapperTest
 * to verify exception mapping without depending on demo entities or database tables.
 */
@Path("/api/test/constraint")
public class ConstraintViolationTestResource {

    @GET
    @Path("/mysql-duplicate")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerMySqlDuplicate() {
        SQLException sqlException = new SQLException(
            "Duplicate entry 'temp' for key 'T_test.UQ_test_name'", "23000", 1062);
        throw new ConstraintViolationException(
            "Duplicate entry 'temp' for key 'T_test.UQ_test_name'", sqlException, "UQ_test_name");
    }

    @GET
    @Path("/h2-unique")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerH2Unique() {
        SQLException sqlException = new SQLException(
            "Unique index or primary key violation: \"UQ_TEST_NAME ON T_TEST(NAME)\"", "23505", 23505);
        throw new ConstraintViolationException(
            "Unique index or primary key violation: \"UQ_TEST_NAME ON T_TEST(NAME)\"", sqlException, "UQ_test_name");
    }

    @GET
    @Path("/fk-violation")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerFkViolation() {
        throw new ConstraintViolationException(
            "Cannot add or update a child row: a foreign key constraint fails",
            new SQLException("Cannot add or update a child row: a foreign key constraint fails", "23000", 1452),
            "FK_some_fk");
    }

    @GET
    @Path("/fk-parent-violation")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerFkParentViolation() {
        throw new ConstraintViolationException(
            "could not execute statement [Cannot delete or update a parent row: a foreign key constraint fails (`db`.`T_child`, CONSTRAINT `FK_child_example_id` FOREIGN KEY (`example_id`) REFERENCES `T_example` (`id`))] [delete from T_example where id=?]",
            new SQLException("Cannot delete or update a parent row", "23000", 1451),
            "FK_child_example_id");
    }

    @GET
    @Path("/fk-by-error-code")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerFkByErrorCode() {
        throw new ConstraintViolationException(
            "could not execute statement",
            new SQLException("Cannot delete or update a parent row", "23000", 1451),
            "FK_child_example_id");
    }

    @GET
    @Path("/uq-name-only")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerUqNameOnly() {
        throw new ConstraintViolationException(
            "could not execute statement",
            new SQLException("constraint violation", "23000", 1),
            "UQ_test_name");
    }

    @GET
    @Path("/null-message")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerNullMessage() {
        SQLException sqlException = new SQLException(
            "Duplicate entry 'x' for key 'T_test.UQ_test_name'", "23000", 1062);
        throw new ConstraintViolationException(null, sqlException, "UQ_test_name");
    }

    @GET
    @Path("/null-cause")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerNullCause() {
        throw new ConstraintViolationException(null, null, "FK_some_fk");
    }

    @GET
    @Path("/unknown-constraint")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerUnknownConstraint() {
        throw new ConstraintViolationException(
            "some other db error",
            new SQLException("generic error", "42000", 9999),
            null);
    }

    @GET
    @Path("/fk-parent-references-style")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerFkParentReferencesStyle() {
        // Message contains REFERENCES `T_example` — exercises extractReferencedEntity REFERENCES path
        throw new ConstraintViolationException(
            "Cannot delete or update a parent row: REFERENCES `T_example` (`id`)",
            new SQLException("Cannot delete or update a parent row", "23000", 1451),
            "FK_child_example_id");
    }

    @GET
    @Path("/fk-parent-t-fallback")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerFkParentTFallback() {
        // Message has T_ but no REFERENCES keyword — exercises T_ fallback in extractReferencedEntity
        throw new ConstraintViolationException(
            "Cannot delete or update a parent row: T_example constraint",
            new SQLException("Cannot delete or update a parent row", "23000", 1451),
            null);
    }

    @GET
    @Path("/fk-constraint-name-only")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerFkConstraintNameOnly() {
        // No message detail at all — exercises FK_ constraint name parsing in extractReferencedEntity
        throw new ConstraintViolationException(
            "Cannot delete or update a parent row",
            new SQLException("Cannot delete or update a parent row", "23000", 1451),
            "FK_order_customer_id");
    }

    @GET
    @Path("/fk-cause-message")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerFkCauseMessage() {
        // Null exception message, cause has FK message — exercises extractForeignKeyDetail null-message path
        throw new ConstraintViolationException(
            null,
            new SQLException("Cannot add or update a child row: foreign key fails", "23000", 1452),
            null);
    }

    @GET
    @Path("/duplicate-no-key")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerDuplicateNoKey() {
        // Duplicate entry message without 'for key' part — exercises partial match in extractDuplicateDetail
        throw new ConstraintViolationException(
            "Duplicate entry 'abc'",
            new SQLException("Duplicate entry 'abc'", "23000", 1062),
            null);
    }

    @GET
    @Path("/h2-unique-null-constraint")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerH2UniqueNullConstraint() {
        // H2 unique violation with null constraint name — exercises null constraint branch in extractDuplicateDetail
        throw new ConstraintViolationException(
            "Unique index or primary key violation: ON T_TEST(NAME)",
            new SQLException("Unique index or primary key violation", "23505", 23505),
            null);
    }
}
