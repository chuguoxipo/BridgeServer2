package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_UPDATE_STUDIES;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_STUDIES;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.models.files.FileDispositionType.INLINE;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.FileService;
import org.sagebionetworks.bridge.services.StudyService;

@CrossOrigin
@RestController
public class StudyController extends BaseController {

    static final StatusMessage DELETED_MSG = new StatusMessage("Study deleted.");
    
    private StudyService service;
    
    private FileService fileService;

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.service = studyService;
    }
    
    @Autowired
    final void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping(path = {"/v5/studies", "/v3/substudies"})
    public PagedResourceList<Study> getStudies(
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize,            
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        UserSession session = getAdministrativeSession();

        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return service.getStudies(session.getAppId(), offsetByInt, pageSizeInt, includeDeleted);
    }

    @PostMapping(path = {"/v5/studies", "/v3/substudies"})
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createStudy() {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, STUDY_DESIGNER, ORG_ADMIN, ADMIN);

        // we don't check if the study coordinator is member of the study because it doesn't
        // exist yet. If the caller is in an organization, that organization will sponsor the
        // created study.
        Study study = parseJson(Study.class);
        
        return service.createStudy(session.getAppId(), study, true);
    }

    @GetMapping(path = {"/v5/studies/{id}", "/v3/substudies/{id}"})
    public Study getStudy(@PathVariable String id) {
        UserSession session = getAdministrativeSession();
        
        CAN_READ_STUDIES.checkAndThrow(STUDY_ID, id);
        
        return service.getStudy(session.getAppId(), id, true);
    }

    @PostMapping(path = {"/v5/studies/{id}", "/v3/substudies/{id}"})
    public VersionHolder updateStudy(@PathVariable String id) {
        UserSession session = getAdministrativeSession();
        
        CAN_UPDATE_STUDIES.checkAndThrow(STUDY_ID, id);

        Study study = parseJson(Study.class);
        study.setIdentifier(id);
        
        return service.updateStudy(session.getAppId(), study);
    }

    @DeleteMapping(path = {"/v5/studies/{id}", "/v3/substudies/{id}"})
    public StatusMessage deleteStudy(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(ADMIN);

        if (physical) {
            service.deleteStudyPermanently(session.getAppId(), id);
        } else {
            service.deleteStudy(session.getAppId(), id);
        }
        return DELETED_MSG;
    }
    
    @PostMapping("/v5/studies/{id}/logo")
    @ResponseStatus(HttpStatus.CREATED)
    public FileRevision createStudyLogo(@PathVariable String id) {
        UserSession session = getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        Study study = service.getStudy(session.getAppId(), id, true);
        
        FileMetadata metadata = null;
        if (study.getLogoGuid() != null) {
            metadata = fileService.getFile(session.getAppId(), study.getLogoGuid());
        } else {
            metadata = new FileMetadata();
            metadata.setName(study.getName() + " Logo");
            metadata.setAppId(session.getAppId());
            metadata.setDisposition(INLINE);
            metadata = fileService.createFile(session.getAppId(), metadata);
            study.setLogoGuid(metadata.getGuid());
            service.updateStudy(session.getAppId(), study);
        }
        FileRevision revision = new FileRevision();
        revision.setFileGuid(metadata.getGuid());
        revision.setDescription(study.getName() + " Logo");
        return fileService.createFileRevision(session.getAppId(), revision);
    }
    
    @PostMapping("/v5/studies/{id}/logo/{createdOn}")
    public Study finishStudyLogo(@PathVariable String id, @PathVariable("createdOn") String createdOnStr) {
        UserSession session = getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);

        Study study = service.getStudy(session.getAppId(), id, true);
        String guid = study.getLogoGuid();
        if (guid == null) {
            throw new BadRequestException("Study logo upload cannot be finished because it was not started.");
        }
        DateTime createdOn = DateTime.parse(createdOnStr);
        
        fileService.finishFileRevision(session.getAppId(), guid, createdOn);
        
        FileRevision revision = fileService.getFileRevision(guid, createdOn)
                .orElseThrow(() -> new EntityNotFoundException(FileRevision.class));
        
        study.setLogoURL(revision.getDownloadURL());
        service.updateStudy(session.getAppId(), study);
        
        return study;
    }

}
