package io.onedev.server.rest.resource;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.shiro.authz.UnauthorizedException;
import org.jspecify.annotations.Nullable;

import io.onedev.server.model.Project;
import io.onedev.server.rest.annotation.Api;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.service.ProjectService;
import io.onedev.server.traceability.gate.TraceabilityGateResult;
import io.onedev.server.traceability.gate.TraceabilityGateService;
import io.onedev.server.traceability.matrix.TraceabilityMatrix;
import io.onedev.server.traceability.matrix.TraceabilityMatrixService;

/**
 * Endpoints REST para consulta y exportación de la Matriz de Trazabilidad Integral (RF4)
 * y evaluación de la Puerta de Bloqueo de Despliegues / Detección de Deriva (RF5).
 */
@Path("/traceability")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class TraceabilityResource {

    private final TraceabilityMatrixService matrixService;
    private final TraceabilityGateService gateService;
    private final ProjectService projectService;

    @Inject
    public TraceabilityResource(
            TraceabilityMatrixService matrixService,
            TraceabilityGateService gateService,
            ProjectService projectService) {
        this.matrixService = matrixService;
        this.gateService = gateService;
        this.projectService = projectService;
    }

    @Api(order=100, description="Get full Traceability Matrix for a project")
    @Path("/matrix/{projectId}")
    @GET
    public Response getMatrix(@PathParam("projectId") Long projectId, @QueryParam("revision") @Nullable String revision) {
        Project project = projectService.load(projectId);
        if (!SecurityUtils.canReadCode(project)) {
            throw new UnauthorizedException();
        }
        TraceabilityMatrix matrix = matrixService.buildMatrix(project, revision);
        return Response.ok(matrix).build();
    }

    @Api(order=200, description="Get forward traceability elements for a requirement or ADR")
    @Path("/forward/{projectId}")
    @GET
    public Response getForward(@PathParam("projectId") Long projectId,
                               @QueryParam("id") String id,
                               @QueryParam("revision") @Nullable String revision) {
        Project project = projectService.load(projectId);
        if (!SecurityUtils.canReadCode(project)) {
            throw new UnauthorizedException();
        }
        return Response.ok(matrixService.getForwardTrace(project, id, revision)).build();
    }

    @Api(order=300, description="Get backward traceability origins for a source file, task or model")
    @Path("/backward/{projectId}")
    @GET
    public Response getBackward(@PathParam("projectId") Long projectId,
                                @QueryParam("path") String path,
                                @QueryParam("revision") @Nullable String revision) {
        Project project = projectService.load(projectId);
        if (!SecurityUtils.canReadCode(project)) {
            throw new UnauthorizedException();
        }
        return Response.ok(matrixService.getBackwardTrace(project, path, revision)).build();
    }

    @Api(order=400, description="Export Traceability Matrix as CSV or Markdown")
    @Path("/export/{projectId}")
    @GET
    public Response export(@PathParam("projectId") Long projectId,
                           @QueryParam("format") @Nullable String format,
                           @QueryParam("revision") @Nullable String revision) {
        Project project = projectService.load(projectId);
        if (!SecurityUtils.canReadCode(project)) {
            throw new UnauthorizedException();
        }
        TraceabilityMatrix matrix = matrixService.buildMatrix(project, revision);

        if ("csv".equalsIgnoreCase(format)) {
            String csv = matrixService.exportToCsv(matrix);
            return Response.ok(csv, "text/csv")
                .header("Content-Disposition", "attachment; filename=\"traceability-matrix-" + project.getName() + ".csv\"")
                .build();
        } else if ("md".equalsIgnoreCase(format) || "markdown".equalsIgnoreCase(format)) {
            String md = matrixService.exportToMarkdown(matrix);
            return Response.ok(md, "text/markdown")
                .header("Content-Disposition", "attachment; filename=\"traceability-matrix-" + project.getName() + ".md\"")
                .build();
        } else {
            return Response.ok(matrixService.exportToJson(matrix)).build();
        }
    }

    @Api(order=500, description="Check Deployment Blocking Gate and Drift Detection (RF5)")
    @Path("/gate/{projectId}")
    @GET
    public Response checkGate(
            @PathParam("projectId") Long projectId,
            @QueryParam("revision") @Nullable String revision,
            @QueryParam("minCoverage") @DefaultValue("80") int minCoverage,
            @QueryParam("maxDrift") @DefaultValue("0") int maxDrift,
            @QueryParam("failOnPending") @DefaultValue("true") boolean failOnPending,
            @QueryParam("strict") @DefaultValue("true") boolean strict) {
        Project project = projectService.load(projectId);
        if (!SecurityUtils.canReadCode(project)) {
            throw new UnauthorizedException();
        }
        TraceabilityGateResult result = gateService.checkGate(
                project, revision, minCoverage, maxDrift, failOnPending, strict);
        return Response.ok(result).build();
    }
}
