package dev.abstratium.core.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;
    private ContainerRequestContext request;
    private ContainerResponseContext response;
    private MultivaluedMap<String, Object> headers;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
        request = null; // not used by the filter
        headers = new MultivaluedHashMap<>();
        response = new StubResponseContext(headers);
    }

    @Test
    void testCspHeaderAddedWhenEnabled() throws Exception {
        filter.cspEnabled = true;
        filter.cspPolicy = "default-src 'self'";
        filter.hstsEnabled = false;
        filter.hstsMaxAge = 31536000;
        filter.hstsIncludeSubDomains = false;
        filter.hstsPreload = false;

        filter.filter(request, response);

        assertEquals("default-src 'self'", headers.getFirst("Content-Security-Policy"));
        assertNotNull(headers.getFirst("X-Content-Type-Options"));
        assertNotNull(headers.getFirst("X-Frame-Options"));
    }

    @Test
    void testCspHeaderNotAddedWhenDisabled() throws Exception {
        filter.cspEnabled = false;
        filter.cspPolicy = "default-src 'self'";
        filter.hstsEnabled = false;
        filter.hstsMaxAge = 31536000;
        filter.hstsIncludeSubDomains = false;
        filter.hstsPreload = false;

        filter.filter(request, response);

        assertNull(headers.getFirst("Content-Security-Policy"));
        assertNotNull(headers.getFirst("X-Content-Type-Options"));
    }

    @Test
    void testHstsHeaderAddedWhenEnabled() throws Exception {
        filter.cspEnabled = false;
        filter.hstsEnabled = true;
        filter.hstsMaxAge = 31536000;
        filter.hstsIncludeSubDomains = true;
        filter.hstsPreload = true;

        filter.filter(request, response);

        String hsts = (String) headers.getFirst("Strict-Transport-Security");
        assertNotNull(hsts);
        assertTrue(hsts.contains("max-age=31536000"));
        assertTrue(hsts.contains("includeSubDomains"));
        assertTrue(hsts.contains("preload"));
    }

    @Test
    void testHstsHeaderWithoutSubdomainsAndPreload() throws Exception {
        filter.cspEnabled = false;
        filter.hstsEnabled = true;
        filter.hstsMaxAge = 86400;
        filter.hstsIncludeSubDomains = false;
        filter.hstsPreload = false;

        filter.filter(request, response);

        String hsts = (String) headers.getFirst("Strict-Transport-Security");
        assertNotNull(hsts);
        assertTrue(hsts.contains("max-age=86400"));
        assertFalse(hsts.contains("includeSubDomains"));
        assertFalse(hsts.contains("preload"));
    }

    @Test
    void testHstsNotAddedWhenDisabled() throws Exception {
        filter.cspEnabled = false;
        filter.hstsEnabled = false;
        filter.hstsMaxAge = 31536000;
        filter.hstsIncludeSubDomains = true;
        filter.hstsPreload = true;

        filter.filter(request, response);

        assertNull(headers.getFirst("Strict-Transport-Security"));
    }

    private static class StubResponseContext implements ContainerResponseContext {
        private final MultivaluedMap<String, Object> headers;

        StubResponseContext(MultivaluedMap<String, Object> headers) {
            this.headers = headers;
        }

        @Override public MultivaluedMap<String, Object> getHeaders() { return headers; }
        @Override public int getStatus() { return 200; }
        @Override public void setStatus(int code) {}
        @Override public Response.StatusType getStatusInfo() { return Response.Status.OK; }
        @Override public void setStatusInfo(Response.StatusType statusInfo) {}
        @Override public Object getEntity() { return null; }
        @Override public Class<?> getEntityClass() { return null; }
        @Override public Type getEntityType() { return null; }
        @Override public void setEntity(Object entity) {}
        @Override public void setEntity(Object entity, Annotation[] annotations, MediaType mediaType) {}
        @Override public Annotation[] getEntityAnnotations() { return new Annotation[0]; }
        @Override public OutputStream getEntityStream() { return null; }
        @Override public void setEntityStream(OutputStream outputStream) {}
        @Override public MultivaluedMap<String, String> getStringHeaders() { return new MultivaluedHashMap<>(); }
        @Override public String getHeaderString(String name) { return null; }
        @Override public Set<String> getAllowedMethods() { return Set.of(); }
        @Override public Date getDate() { return null; }
        @Override public Locale getLanguage() { return null; }
        @Override public int getLength() { return -1; }
        @Override public MediaType getMediaType() { return null; }
        @Override public Map<String, NewCookie> getCookies() { return Map.of(); }
        @Override public EntityTag getEntityTag() { return null; }
        @Override public Date getLastModified() { return null; }
        @Override public URI getLocation() { return null; }
        @Override public Set<Link> getLinks() { return Set.of(); }
        @Override public boolean hasLink(String relation) { return false; }
        @Override public Link getLink(String relation) { return null; }
        @Override public Link.Builder getLinkBuilder(String relation) { return null; }
        @Override public boolean hasEntity() { return false; }
    }
}
