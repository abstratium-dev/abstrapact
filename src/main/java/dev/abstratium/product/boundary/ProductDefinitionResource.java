package dev.abstratium.product.boundary;

import dev.abstratium.core.service.JwtOrgResolver;
import dev.abstratium.product.entity.ProductDefinition;
import dev.abstratium.product.service.ProductDefinitionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Path("/api/v1/product-definitions")
@Tag(name = "Product Definitions", description = "Operations for managing product definitions")
@RolesAllowed("abstratium-abstrapact_user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductDefinitionResource {

    @Inject
    ProductDefinitionService service;

    @GET
    @Operation(summary = "List all product definitions")
    public List<ProductDefinition> listAll() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a product definition by ID")
    public Response getById(@PathParam("id") String id) {
        Optional<ProductDefinition> product = service.findById(id);
        if (product.isPresent()) {
            return Response.ok(product.get()).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/code/{productCode}")
    @Operation(summary = "Get a product definition by product code")
    public Response getByProductCode(@PathParam("productCode") String productCode) {
        Optional<ProductDefinition> product = service.findByProductCode(productCode);
        if (product.isPresent()) {
            return Response.ok(product.get()).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Operation(summary = "Create a new product definition")
    public Response create(ProductDefinition definition) {
        if (definition.getProductCode() == null || definition.getProductCode().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Product code is required").build();
        }
        if (service.existsByProductCode(definition.getProductCode())) {
            return Response.status(Response.Status.CONFLICT)
                .entity("Product code already exists").build();
        }
        ProductDefinition created = service.createProductDefinition(definition);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update an existing product definition")
    public Response update(@PathParam("id") String id, ProductDefinition definition) {
        Optional<ProductDefinition> existing = service.findById(id);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        definition.setId(id);
        ProductDefinition updated = service.updateProductDefinition(definition);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a product definition")
    public Response delete(@PathParam("id") String id) {
        Optional<ProductDefinition> existing = service.findById(id);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        service.deleteProductDefinition(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/billing-model/{billingModel}")
    @Operation(summary = "List product definitions by billing model")
    public List<ProductDefinition> listByBillingModel(
            @PathParam("billingModel") ProductDefinition.BillingModel billingModel) {
        return service.findByBillingModel(billingModel);
    }

    @POST
    @Path("/import/yaml")
    @Consumes("application/x-yaml")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Import a product definition from YAML")
    public Response importFromYaml(String yamlContent) {
        ProductDefinition definition = parseYamlToProductDefinition(yamlContent);
        if (service.existsByProductCode(definition.getProductCode())) {
            return Response.status(Response.Status.CONFLICT)
                .entity("Product code already exists").build();
        }
        ProductDefinition created = service.createProductDefinition(definition);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}/export/yaml")
    @Produces("application/x-yaml")
    @Operation(summary = "Export a product definition to YAML")
    public Response exportToYaml(@PathParam("id") String id) {
        Optional<ProductDefinition> product = service.findById(id);
        if (product.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String yaml = convertProductDefinitionToYaml(product.get());
        return Response.ok(yaml)
            .header("Content-Disposition", "attachment; filename=\"" + product.get().getProductCode() + ".yaml\"")
            .build();
    }

    private ProductDefinition parseYamlToProductDefinition(String yamlContent) {
        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        String[] lines = yamlContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("organisation_id:")) {
                definition.setOrganisationId(line.substring("organisation_id:".length()).trim());
            } else if (line.startsWith("product_code:")) {
                definition.setProductCode(line.substring("product_code:".length()).trim());
            } else if (line.startsWith("description:")) {
                definition.setDescription(line.substring("description:".length()).trim());
            } else if (line.startsWith("billing_model:")) {
                String model = line.substring("billing_model:".length()).trim().toUpperCase();
                definition.setBillingModel(ProductDefinition.BillingModel.valueOf(model));
            } else if (line.startsWith("valid_from:")) {
                definition.setProductValidFrom(LocalDate.parse(line.substring("valid_from:".length()).trim()));
            } else if (line.startsWith("valid_until:")) {
                String value = line.substring("valid_until:".length()).trim();
                if (!value.isEmpty() && !value.equals("null")) {
                    definition.setProductValidUntil(LocalDate.parse(value));
                }
            }
        }
        return definition;
    }

    private String convertProductDefinitionToYaml(ProductDefinition definition) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("organisation_id: ").append(definition.getOrganisationId()).append("\n");
        yaml.append("product_code: ").append(definition.getProductCode()).append("\n");
        yaml.append("description: ").append(definition.getDescription()).append("\n");
        yaml.append("billing_model: ").append(definition.getBillingModel().name()).append("\n");
        if (definition.getProductValidFrom() != null) {
            yaml.append("valid_from: ").append(definition.getProductValidFrom()).append("\n");
        }
        if (definition.getProductValidUntil() != null) {
            yaml.append("valid_until: ").append(definition.getProductValidUntil()).append("\n");
        } else {
            yaml.append("valid_until: null\n");
        }
        return yaml.toString();
    }
}
