package dev.abstratium.product.boundary;

import dev.abstratium.product.boundary.dto.CompleteProductResponse;
import dev.abstratium.product.boundary.dto.ProductDefinitionRequest;
import dev.abstratium.product.entity.PartAttributeDefinition;
import dev.abstratium.product.entity.PartDefinition;
import dev.abstratium.product.entity.ProductDefinition;
import dev.abstratium.product.service.ProductDefinitionService;
import jakarta.annotation.security.RolesAllowed;
import dev.abstratium.core.service.CurrentOrgContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Path("/api/product-definitions")
@Tag(name = "Product Definitions", description = "Operations for managing product definitions")
@RolesAllowed("abstratium-abstrapact_user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductDefinitionResource {

    @Inject
    ProductDefinitionService service;

    @Inject
    CurrentOrgContext currentOrgContext;

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
        if (service.existsByRawProductCode(definition.getProductCode())) {
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

    // ==================== Complete Product with Parts ====================

    @POST
    @Path("/complete")
    @Operation(summary = "Create a complete product definition with parts, attributes, and allowed values")
    public Response createComplete(ProductDefinitionRequest request) {
        if (request.getProductCode() == null || request.getProductCode().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Product code is required").build();
        }
        if (service.existsByRawProductCode(request.getProductCode())) {
            return Response.status(Response.Status.CONFLICT)
                .entity("Product code already exists").build();
        }
        ProductDefinition created = service.createCompleteProduct(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}/complete")
    @Operation(summary = "Update a complete product definition with parts, attributes, and allowed values")
    public Response updateComplete(@PathParam("id") String id, ProductDefinitionRequest request) {
        Optional<ProductDefinition> existing = service.findById(id);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            ProductDefinition updated = service.updateCompleteProduct(id, request);
            return Response.ok(updated).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}/complete")
    @Operation(summary = "Delete a complete product definition with all its parts")
    public Response deleteComplete(@PathParam("id") String id) {
        Optional<ProductDefinition> existing = service.findById(id);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        service.deleteCompleteProduct(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/complete")
    @Operation(summary = "Get the complete product definition with full part tree and attributes")
    public Response getComplete(@PathParam("id") String id) {
        Optional<ProductDefinition> existing = service.findById(id);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        CompleteProductResponse response = service.findCompleteProduct(id);
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}/parts")
    @Operation(summary = "Get all parts for a product definition")
    public Response getPartsByProductId(@PathParam("id") String id) {
        Optional<ProductDefinition> product = service.findById(id);
        if (product.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<PartDefinition> parts = service.findPartsByProductId(id);
        return Response.ok(parts).build();
    }

    // ==================== Part Management ====================

    @GET
    @Path("/parts/{partId}")
    @Operation(summary = "Get a part by ID")
    public Response getPartById(@PathParam("partId") String partId) {
        Optional<PartDefinition> part = service.findPartById(partId);
        if (part.isPresent()) {
            return Response.ok(part.get()).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Path("/{id}/parts")
    @Operation(summary = "Add a part to a product definition")
    public Response addPart(@PathParam("id") String id, PartDefinition part) {
        Optional<ProductDefinition> product = service.findById(id);
        if (product.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        part.setProductDefinition(product.get());
        PartDefinition created = service.createPart(part);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/parts/{partId}")
    @Operation(summary = "Update a part")
    public Response updatePart(@PathParam("partId") String partId, PartDefinition part) {
        Optional<PartDefinition> existing = service.findPartById(partId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        part.setId(partId);
        // Preserve the product definition reference from the existing part
        part.setProductDefinition(existing.get().getProductDefinition());
        PartDefinition updated = service.updatePart(part);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/parts/{partId}")
    @Operation(summary = "Delete a part")
    public Response deletePart(@PathParam("partId") String partId) {
        Optional<PartDefinition> existing = service.findPartById(partId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        service.deletePart(partId);
        return Response.noContent().build();
    }

    // ==================== Part Attribute Management ====================

    @GET
    @Path("/parts/{partId}/attributes")
    @Operation(summary = "Get all attributes for a part")
    public Response getAttributesByPartId(@PathParam("partId") String partId) {
        Optional<PartDefinition> part = service.findPartById(partId);
        if (part.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<PartAttributeDefinition> attributes = service.findAttributesByPartId(partId);
        return Response.ok(attributes).build();
    }

    @GET
    @Path("/attributes/{attributeId}")
    @Operation(summary = "Get an attribute by ID")
    public Response getAttributeById(@PathParam("attributeId") String attributeId) {
        Optional<PartAttributeDefinition> attribute = service.findAttributeById(attributeId);
        if (attribute.isPresent()) {
            return Response.ok(attribute.get()).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Path("/parts/{partId}/attributes")
    @Operation(summary = "Add an attribute to a part")
    public Response addAttribute(@PathParam("partId") String partId, PartAttributeDefinition attribute) {
        Optional<PartDefinition> part = service.findPartById(partId);
        if (part.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        attribute.setPartDefinition(part.get());
        PartAttributeDefinition created = service.createAttribute(attribute);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/attributes/{attributeId}")
    @Operation(summary = "Update an attribute")
    public Response updateAttribute(@PathParam("attributeId") String attributeId, PartAttributeDefinition attribute) {
        Optional<PartAttributeDefinition> existing = service.findAttributeById(attributeId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        attribute.setId(attributeId);
        PartAttributeDefinition updated = service.updateAttribute(attribute);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/attributes/{attributeId}")
    @Operation(summary = "Delete an attribute")
    public Response deleteAttribute(@PathParam("attributeId") String attributeId) {
        Optional<PartAttributeDefinition> existing = service.findAttributeById(attributeId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        service.deleteAttribute(attributeId);
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
        if (service.existsByRawProductCode(definition.getProductCode())) {
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
        definition.setOrganisationId(currentOrgContext.getOrgId());
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
            } else if (line.startsWith("payment_model:")) {
                String model = line.substring("payment_model:".length()).trim().toUpperCase();
                definition.setPaymentModel(ProductDefinition.PaymentModel.valueOf(model));
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
        yaml.append("payment_model: ").append(definition.getPaymentModel().name()).append("\n");
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
