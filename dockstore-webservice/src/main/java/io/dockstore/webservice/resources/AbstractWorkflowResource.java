package io.dockstore.webservice.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Checksum;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.CacheConfigManager;
import io.dockstore.webservice.helpers.FileFormatHelper;
import io.dockstore.webservice.helpers.GitHubHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.swagger.annotations.Api;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.core.WorkflowMode.DOCKSTORE_YML;
import static io.dockstore.webservice.core.WorkflowMode.FULL;
import static io.dockstore.webservice.core.WorkflowMode.STUB;

/**
 * Base class for ServiceResource and WorkflowResource.
 *
 * Mainly has GitHub app logic, although there is also some BitBucket refresh
 * token logic that was easier to move in here than refactor out.
 *
 * @param <T>
 */
@Api("workflows")
public abstract class AbstractWorkflowResource<T extends Workflow> implements SourceControlResourceInterface, AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractWorkflowResource.class);
    private static final String SHA_TYPE_FOR_SOURCEFILES = "SHA-1";

    protected final HttpClient client;
    protected final TokenDAO tokenDAO;
    protected final WorkflowDAO workflowDAO;
    protected final UserDAO userDAO;
    protected final WorkflowVersionDAO workflowVersionDAO;
    protected final EventDAO eventDAO;
    protected final FileDAO fileDAO;
    protected final String gitHubPrivateKeyFile;
    protected final String gitHubAppId;
    protected final SessionFactory sessionFactory;

    private final String bitbucketClientSecret;
    private final String bitbucketClientID;
    private final Class<T> entityClass;

    private final double dockstoreYMLV11 = 1.1;
    private final double dockstoreYMLV12 = 1.2;

    public AbstractWorkflowResource(HttpClient client, SessionFactory sessionFactory, DockstoreWebserviceConfiguration configuration, Class<T> clazz) {
        this.client = client;
        this.sessionFactory = sessionFactory;

        this.tokenDAO = new TokenDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
        this.bitbucketClientID = configuration.getBitbucketClientID();
        this.bitbucketClientSecret = configuration.getBitbucketClientSecret();
        gitHubPrivateKeyFile = configuration.getGitHubAppPrivateKeyFile();
        gitHubAppId = configuration.getGitHubAppId();

        this.entityClass = clazz;
    }

    protected List<Token> checkOnBitbucketToken(User user) {
        List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            String refreshUrl = BITBUCKET_URL + "site/oauth2/access_token";
            String payload = "grant_type=refresh_token&refresh_token=" + bitbucketToken.getRefreshToken();
            refreshToken(refreshUrl, bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret, payload);
        }

        return tokenDAO.findByUserId(user.getId());
    }

    /**
     * Finds all workflows from a general Dockstore path that are of type FULL
     * @param dockstoreWorkflowPath Dockstore path (ex. github.com/dockstore/dockstore-ui2)
     * @return List of FULL workflows with the given Dockstore path
     */
    protected List<Workflow> findAllWorkflowsByPath(String dockstoreWorkflowPath, WorkflowMode workflowMode) {
        return workflowDAO.findAllByPath(dockstoreWorkflowPath, false)
                .stream()
                .filter(workflow ->
                        workflow.getMode() == workflowMode)
                .collect(Collectors.toList());
    }

    protected SourceCodeRepoInterface getSourceCodeRepoInterface(String gitUrl, User user) {
        List<Token> tokens = checkOnBitbucketToken(user);

        final String bitbucketTokenContent = getToken(tokens, TokenType.BITBUCKET_ORG);
        final String gitHubTokenContent = getToken(tokens, TokenType.GITHUB_COM);
        final String gitlabTokenContent = getToken(tokens, TokenType.GITLAB_COM);

        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory
            .createSourceCodeRepo(gitUrl, client, bitbucketTokenContent, gitlabTokenContent, gitHubTokenContent);
        if (sourceCodeRepo == null) {
            throw new CustomWebApplicationException("Git tokens invalid, please re-link your git accounts.", HttpStatus.SC_BAD_REQUEST);
        }
        return sourceCodeRepo;
    }

    private String getToken(List<Token> tokens, TokenType tokenType) {
        final Token token = Token.extractToken(tokens, tokenType);
        return token == null ? null : token.getContent();
    }

    /**
     * Updates the existing workflow in the database with new information from newWorkflow, including new, updated, and removed
     * workflow verions.
     * @param workflow    workflow to be updated
     * @param newWorkflow workflow to grab new content from
     */
    protected void updateDBWorkflowWithSourceControlWorkflow(Workflow workflow, Workflow newWorkflow, final User user) {
        // update root workflow
        workflow.update(newWorkflow);
        // update workflow versions
        Map<String, WorkflowVersion> existingVersionMap = new HashMap<>();
        workflow.getWorkflowVersions().forEach(version -> existingVersionMap.put(version.getName(), version));

        // delete versions that exist in old workflow but do not exist in newWorkflow
        Map<String, WorkflowVersion> newVersionMap = new HashMap<>();
        newWorkflow.getWorkflowVersions().forEach(version -> newVersionMap.put(version.getName(), version));
        Sets.SetView<String> removedVersions = Sets.difference(existingVersionMap.keySet(), newVersionMap.keySet());
        for (String version : removedVersions) {
            workflow.removeWorkflowVersion(existingVersionMap.get(version));
        }

        // Then copy over content that changed
        for (WorkflowVersion version : newWorkflow.getWorkflowVersions()) {
            // skip frozen versions
            WorkflowVersion workflowVersionFromDB = existingVersionMap.get(version.getName());
            if (existingVersionMap.containsKey(version.getName())) {
                if (workflowVersionFromDB.isFrozen()) {
                    continue;
                }
                workflowVersionFromDB.update(version);
            } else {
                // create a new one and replace the old one
                final long workflowVersionId = workflowVersionDAO.create(version);
                workflowVersionFromDB = workflowVersionDAO.findById(workflowVersionId);
                this.eventDAO.createAddTagToEntryEvent(user, workflow, workflowVersionFromDB);
                workflow.getWorkflowVersions().add(workflowVersionFromDB);
                existingVersionMap.put(workflowVersionFromDB.getName(), workflowVersionFromDB);
            }

            // Update source files for each version
            Map<String, SourceFile> existingFileMap = new HashMap<>();
            workflowVersionFromDB.getSourceFiles().forEach(file -> existingFileMap.put(file.getType().toString() + file.getAbsolutePath(), file));

            for (SourceFile file : version.getSourceFiles()) {
                String fileKey = file.getType().toString() + file.getAbsolutePath();
                SourceFile existingFile = existingFileMap.get(fileKey);
                if (existingFileMap.containsKey(fileKey)) {
                    List<Checksum> checksums = new ArrayList<>();
                    Optional<String> sha = FileFormatHelper.calcSHA1(file.getContent());
                    if (sha.isPresent()) {
                        checksums.add(new Checksum(SHA_TYPE_FOR_SOURCEFILES, sha.get()));
                        if (existingFile.getChecksums() == null) {
                            existingFile.setChecksums(checksums);
                        } else {
                            existingFile.getChecksums().clear();
                            existingFileMap.get(fileKey).getChecksums().addAll(checksums);

                        }
                    }
                    existingFile.setContent(file.getContent());
                } else {
                    final long fileID = fileDAO.create(file);
                    final SourceFile fileFromDB = fileDAO.findById(fileID);

                    Optional<String> sha = FileFormatHelper.calcSHA1(file.getContent());
                    if (sha.isPresent()) {
                        fileFromDB.getChecksums().add(new Checksum(SHA_TYPE_FOR_SOURCEFILES, sha.get()));
                    }
                    workflowVersionFromDB.getSourceFiles().add(fileFromDB);
                }
            }

            // Remove existing files that are no longer present on remote
            for (Map.Entry<String, SourceFile> entry : existingFileMap.entrySet()) {
                boolean toDelete = true;
                for (SourceFile file : version.getSourceFiles()) {
                    if (entry.getKey().equals(file.getType().toString() + file.getAbsolutePath())) {
                        toDelete = false;
                    }
                }
                if (toDelete) {
                    workflowVersionFromDB.getSourceFiles().remove(entry.getValue());
                }
            }

            // Update the validations
            for (Validation versionValidation : version.getValidations()) {
                workflowVersionFromDB.addOrUpdateValidation(versionValidation);
            }
        }
    }

    /**
     * Handle webhooks from GitHub apps (redirected from AWS Lambda)
     * - Create services and workflows when necessary
     * - Add or update version for corresponding service and workflow
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param username Username of GitHub user that triggered action
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param installationId GitHub App installation ID
     * @return List of new and updated workflows
     */
    protected List<Workflow> githubWebhookRelease(String repository, String username, String gitReference, String installationId) {
        // Retrieve the user who triggered the call (must exist on Dockstore if workflow is not already present)
        User user = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username, false);

        // Get Installation Access Token
        String installationAccessToken = gitHubAppSetup(installationId);

        // Grab Dockstore YML from GitHub
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationAccessToken);
        SourceFile dockstoreYml = gitHubSourceCodeRepo.getDockstoreYml(repository, gitReference);

        // Parse Dockstore YML and perform appropriate actions
        Yaml yaml = new Yaml();
        try {
            Map<String, Object> map = yaml.load(dockstoreYml.getContent());
            double versionString = (double)map.get("version");

            if (Objects.equals(dockstoreYMLV11, versionString)) {
                // 1.1 - Only works with services
                return createServicesAndVersionsFromDockstoreYml(dockstoreYml, repository, gitReference, gitHubSourceCodeRepo, user, map);
            } else if (Objects.equals(dockstoreYMLV12, versionString)) {
                // 1.2 - Currently only supports workflows, though will eventually support services
                if (map.containsKey("services")) {
                    LOG.info("Services are not yet implemented for version 1.2");
                }

                if (map.containsKey("workflows")) {
                    return createBioWorkflowsAndVersionsFromDockstoreYml(dockstoreYml, repository, gitReference, map, gitHubSourceCodeRepo, user);
                }

                String msg = "Invalid .dockstore.yml";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            } else {
                String msg = versionString + " is not a valid version";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            }
        } catch (YAMLException | ClassCastException | NullPointerException ex) {
            String msg = "Invalid .dockstore.yml";
            LOG.info(msg, ex);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
    }

    /**
     * Create or retrieve workflows based on Dockstore.yml, add or update tag version
     * ONLY WORKS FOR v1.2
     * @param dockstoreYml Dockstore YAML File
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param yml Dockstore YML map
     * @param gitHubSourceCodeRepo Source Code Repo
     * @param user User that triggered action
     * @return List of new and updated workflows
     */
    private List<Workflow> createBioWorkflowsAndVersionsFromDockstoreYml(SourceFile dockstoreYml, String repository, String gitReference, Map<String, Object> yml, GitHubSourceCodeRepo gitHubSourceCodeRepo, User user) {
        try {
            List<Map<String, Object>> workflows = (List<Map<String, Object>>)yml.get("workflows");
            List<Workflow> updatedWorkflows = new ArrayList<>();
            for (Map<String, Object> wf : workflows) {
                String subclass = (String)wf.get("subclass");
                String workflowName = (String)wf.get("name");

                Workflow workflow = createOrGetWorkflow(BioWorkflow.class, repository, user, workflowName, subclass, gitHubSourceCodeRepo);
                workflow = addDockstoreYmlVersionToWorkflow(repository, gitReference, dockstoreYml, gitHubSourceCodeRepo, workflow);
                updatedWorkflows.add(workflow);
            }
            return updatedWorkflows;
        } catch (ClassCastException ex) {
            String msg = "Could not parse workflow array from YML.";
            LOG.info(msg, ex);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
    }

    /**
     * Create or retrieve services based on Dockstore.yml, add or update tag version
     * ONLY WORKS FOR v1.1
     * @param dockstoreYml Dockstore YAML File
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param gitHubSourceCodeRepo Source Code Repo
     * @param user User that triggered action
     * @param yml Dockstore YML map
     * @return List of new and updated services
     */
    private List<Workflow> createServicesAndVersionsFromDockstoreYml(SourceFile dockstoreYml, String repository, String gitReference, GitHubSourceCodeRepo gitHubSourceCodeRepo, User user, Map<String, Object> yml) {
        List<Workflow> updatedServices = new ArrayList<>();
        // TODO: Currently only supports one service per .dockstore.yml
        String subclass;
        try {
            Map<String, Object> serviceObject = (Map<String, Object>)yml.get("service");
            subclass = (String)serviceObject.get("type");
            if (subclass == null) {
                String msg = "Missing required type field.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            }
        } catch (ClassCastException ex) {
            String msg = "Could not parse .dockstore.yml";
            LOG.info(msg, ex);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
        Workflow workflow = createOrGetWorkflow(Service.class, repository, user, "", subclass, gitHubSourceCodeRepo);
        workflow = addDockstoreYmlVersionToWorkflow(repository, gitReference, dockstoreYml, gitHubSourceCodeRepo, workflow);
        updatedServices.add(workflow);

        return updatedServices;
    }

    /**
     * Create or retrieve workflow or service based on Dockstore.yml
     * @param workflowType Either BioWorkflow.class or Service.class
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param user User that triggered action
     * @param workflowName User that triggered action
     * @param subclass Subclass of the workflow
     * @param gitHubSourceCodeRepo Source Code Repo
     * @return New or updated workflow
     */
    private Workflow createOrGetWorkflow(Class workflowType, String repository, User user, String workflowName, String subclass, GitHubSourceCodeRepo gitHubSourceCodeRepo) {
        // Check for existing workflow
        String dockstoreWorkflowPath = "github.com/" + repository + (workflowName != null && !workflowName.isEmpty() ? "/" + workflowName : "");
        Optional<Workflow> workflow = workflowDAO.findByPath(dockstoreWorkflowPath, false, workflowType);

        Workflow workflowToUpdate = null;
        // Create workflow if one does not exist
        if (workflow.isEmpty()) {
            // Ensure that a Dockstore user exists to add to the workflow
            if (user == null) {
                String msg = "User does not have an account on Dockstore.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            }

            if (workflowType == BioWorkflow.class) {
                workflowToUpdate = gitHubSourceCodeRepo.initializeWorkflowFromGitHub(repository, subclass, workflowName);
            } else if (workflowType == Service.class) {
                workflowToUpdate = gitHubSourceCodeRepo.initializeServiceFromGitHub(repository, subclass);
            } else {
                LOG.error(workflowType.getCanonicalName()  + " is not a valid workflow type.");
                throw new CustomWebApplicationException("Currently only workflows and services are supported by GitHub Apps.", LAMBDA_FAILURE);
            }
            long workflowId = workflowDAO.create(workflowToUpdate);
            workflowToUpdate = workflowDAO.findById(workflowId);
            LOG.info("Workflow " + dockstoreWorkflowPath + " has been created.");
        } else {
            workflowToUpdate = workflow.get();

            if (Objects.equals(workflowToUpdate.getMode(), FULL) || Objects.equals(workflowToUpdate.getMode(), STUB)) {
                LOG.info("Converting workflow to DOCKSTORE_YML");
                workflowToUpdate.setMode(DOCKSTORE_YML);
                workflowToUpdate.setDefaultWorkflowPath("/.dockstore.yml");
            }
        }

        if (user != null) {
            workflowToUpdate.getUsers().add(user);
        }

        return  workflowToUpdate;
    }

    /**
     * Add versions to a service or workflow based on Dockstore.yml
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param dockstoreYml Dockstore YAML File
     * @param gitHubSourceCodeRepo Source Code Repo
     * @return New or updated workflow
     */
    private Workflow addDockstoreYmlVersionToWorkflow(String repository, String gitReference, SourceFile dockstoreYml, GitHubSourceCodeRepo gitHubSourceCodeRepo, Workflow workflow) {
        try {
            // Create version and pull relevant files
            WorkflowVersion workflowVersion = gitHubSourceCodeRepo.createVersionForWorkflow(repository, gitReference, workflow, dockstoreYml);
            workflowVersion.setReferenceType(getReferenceTypeFromGitRef(gitReference));
            workflow.addWorkflowVersion(workflowVersion);
            LOG.info("Version " + workflowVersion.getName() + " has been added to workflow " + workflow.getWorkflowPath() + ".");
        } catch (IOException ex) {
            String msg = "Cannot retrieve the workflow reference from GitHub, ensure that " + gitReference + " is a valid tag.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
        return workflow;
    }

    private Version.ReferenceType getReferenceTypeFromGitRef(String gitRef) {
        if (gitRef.startsWith("refs/heads/")) {
            return Version.ReferenceType.BRANCH;
        } else if (gitRef.startsWith("refs/tags/")) {
            return Version.ReferenceType.TAG;
        } else {
            return Version.ReferenceType.NOT_APPLICABLE;
        }
    }

    /**
     * Add user to any existing Dockstore workflow and services from GitHub apps they should own
     * @param user
     */
    protected void syncEntitiesForUser(User user) {
        List<Token> githubByUserId = tokenDAO.findGithubByUserId(user.getId());

        if (githubByUserId.isEmpty()) {
            String msg = "The user does not have a GitHub token, please create one";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        } else {
            syncEntities(user, githubByUserId.get(0));
        }
    }

    /**
     * Syncs entities based on GitHub app installation, optionally limiting to orgs in the GitHub organization <code>organization</code>.
     *
     * 1. Finds all repos that have the Dockstore GitHub app installed
     * 2. For existing entities, ensures that <code>user</code> is one of the entity's users
     *
     * @param user
     * @param gitHubToken
     */
    private void syncEntities(User user, Token gitHubToken) {
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(gitHubToken, client);

        // Get all GitHub repositories for the user
        final Map<String, String> workflowGitUrl2Name = gitHubSourceCodeRepo.getWorkflowGitUrl2RepositoryId();

        // Filter by organization if necessary
        final Collection<String> repositories = workflowGitUrl2Name.values();

        // Add user to any services they should have access to that already exist on Dockstore
        final List<Workflow> existingWorkflows = findDockstoreWorkflowsForGitHubRepos(repositories);
        existingWorkflows.stream()
                .filter(workflow -> !workflow.getUsers().contains(user))
                .forEach(workflow -> workflow.getUsers().add(user));

        // No longer adds stub services, though code could be useful
        //        final Set<String> existingWorkflowPaths = existingWorkflows.stream()
        //                .map(workflow -> workflow.getWorkflowPath()).collect(Collectors.toSet());
        //
        //        GitHubHelper.checkJWT(gitHubAppId, gitHubPrivateKeyFile);
        //
        //        GitHubHelper.reposToCreateEntitiesFor(repositories, organization, existingWorkflowPaths).stream()
        //                .forEach(repositoryName -> {
        //                    final T entity = initializeEntity(repositoryName, gitHubSourceCodeRepo);
        //                    entity.addUser(user);
        //                    final long entityId = workflowDAO.create(entity);
        //                    final Workflow createdEntity = workflowDAO.findById(entityId);
        //                    final Workflow updatedEntity = gitHubSourceCodeRepo.getWorkflow(repositoryName, Optional.of(createdEntity));
        //                    updateDBWorkflowWithSourceControlWorkflow(createdEntity, updatedEntity, user);
        //                });
    }

    /**
     * From the collection of GitHub repositories, returns the list of Dockstore entities (Service or BioWorkflow) that
     * exist for those repositories.
     *
     * Ideally this would return <code>List&lt;T&gt;</code>, but not sure if I can use getClass instead of WorkflowMode
     * for workflows (would it apply to both STUB and WORKFLOW?) in filter call below (see TODO)?
     *
     * @param repositories
     * @return
     */
    private List<Workflow> findDockstoreWorkflowsForGitHubRepos(Collection<String> repositories) {
        final List<String> workflowPaths = repositories.stream().map(repositoryName -> "github.com/" + repositoryName)
                .collect(Collectors.toList());
        return workflowDAO.findByPaths(workflowPaths, false).stream()
                // TODO: Revisit this when support for workflows added.
                .filter(workflow -> Objects.equals(workflow.getMode(), DOCKSTORE_YML))
                .collect(Collectors.toList());
    }

    /**
     * Setup tokens required for GitHub apps
     * @param installationId App installation ID (per repository)
     * @return Installation access token for the given repository
     */
    private String gitHubAppSetup(String installationId) {
        GitHubHelper.checkJWT(gitHubAppId, gitHubPrivateKeyFile);
        String installationAccessToken = CacheConfigManager.getInstance().getInstallationAccessTokenFromCache(installationId);
        if (installationAccessToken == null) {
            String msg = "Could not get an installation access token for install with id " + installationId;
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return installationAccessToken;
    }
}
