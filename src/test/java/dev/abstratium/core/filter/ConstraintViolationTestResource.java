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
            "could not execute statement [Cannot delete or update a parent row: a foreign key constraint fails (`abstoggle`.`T_toggle_stage_rule`, CONSTRAINT `FK_toggle_stage_rule_stage_id` FOREIGN KEY (`stage_id`) REFERENCES `T_stage` (`id`))] [delete from T_stage where id=?]",
            new SQLException("Cannot delete or update a parent row", "23000", 1451),
            "FK_toggle_stage_rule_stage_id");
    }

    @GET
    @Path("/fk-by-error-code")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerFkByErrorCode() {
        throw new ConstraintViolationException(
            "could not execute statement",
            new SQLException("Cannot delete or update a parent row", "23000", 1451),
            "FK_toggle_stage_rule_stage_id");
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
}
