package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;

public interface AssessmentDao {
    /**
     * The paged resource list returned from the DAO contains only the current page of results
     * and the total number of records. Request parameters should be added by the service.
     */
    PagedResourceList<Assessment> getAssessments(String appId, int offsetBy, 
            int pageSize, Set<String> tags, boolean includeDeleted);

    /**
     * The paged resource list returned from the DAO contains only the current page of results
     * and the total number of records. Request parameters should be added by the service.
     */
    PagedResourceList<Assessment> getAssessmentRevisions(
            String appId, String identifier, int offsetBy, int pageSize, boolean includeDeleted);
    
    Optional<Assessment> getAssessment(String appId, String guid);
    
    Optional<Assessment> getAssessment(String appId, String identifier, int revision);
    
    /**
     * This performs a merge of the assessment with persisted objects, including tags. 
     * It can be called to create or update an assessment.
     */
    Assessment saveAssessment(String appId, Assessment assessment);
    
    /**
     * Publication changes two objects at the same time and requires a transaction. Method returns 
     * the original assessment updated to reflect that it is now derived from the shared assessment.
     */
    Assessment publishAssessment(String originAppId, Assessment origin, Assessment dest,
            List<AssessmentResource> destResources);

    /**
     * Import does not change the shared assessment. It creates the resources in the destination app space
     * and returns the newly created assessment in the destination app context.
     */
    Assessment importAssessment(String destAppId, Assessment dest, List<AssessmentResource> destResources);
    
    /**
     * This is an actual delete from the database.
     */
    void deleteAssessment(String appId, Assessment assessment);
}