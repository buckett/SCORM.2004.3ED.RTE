package org.sakaiproject.scorm.entity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.CoreEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityURLRedirect;
import org.sakaiproject.entitybroker.entityprovider.capabilities.ActionsExecutable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.AutoRegisterEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Outputable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.PropertyProvideable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.RESTful;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Redirectable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.RequestAware;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Resolvable;
import org.sakaiproject.entitybroker.entityprovider.extension.ActionReturn;
import org.sakaiproject.entitybroker.entityprovider.extension.Formats;
import org.sakaiproject.entitybroker.entityprovider.extension.RequestGetter;
import org.sakaiproject.entitybroker.entityprovider.search.Search;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.scorm.model.api.ContentPackage;
import org.sakaiproject.scorm.service.api.ScormContentService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.util.ResourceLoader;

/**
 * Allows some basic functions on SCORM module instances via the EntityBroker.
 * 
 * @author bjones86
 */
public class ScormEntityProviderImpl implements ScormEntityProvider, CoreEntityProvider, AutoRegisterEntityProvider, RequestAware,
                                                PropertyProvideable, Resolvable, Outputable, RESTful, Redirectable, ActionsExecutable
{
    // Class members
    private static final Log    LOG                         = LogFactory.getLog( ScormEntityProviderImpl.class );
    private static final String TOOL_CONFIG_PERM            = "scorm.configure";
    private static final String TOOL_LAUNCH_PERM            = "scorm.launch";
    private static final String TOOL_REG_NAME               = "sakai.scorm.tool";
    private static final String SCORM_PLAYER_PAGE_URL_PART  = "wicket:bookmarkablePage=ScormPlayer:org.sakaiproject.scorm.ui.player.pages.PlayerPage";

    // Instance members
    private final ResourceLoader resourceLoader = new ResourceLoader( "messages" );

    // Sakai APIs
    @Getter @Setter private SessionManager              sessionManager;
    @Getter @Setter private SiteService                 siteService;
    @Getter @Setter private SecurityService             securityService;
    @Getter @Setter private UserDirectoryService        userDirectoryService;
    @Getter @Setter private RequestGetter               requestGetter;
    @Getter @Setter private ServerConfigurationService  serverConfigurationService;

    // SCORM APIs
    @Getter @Setter private ScormContentService contentService;

    // *************************************************************
    // ************** Public EntityBroker Methods ******************
    // *************************************************************

    /**
     * Controls the globally unique prefix for the entities handled by this provider. For
     * example: Announcements might use "annc", Evaluation might use "eval" (if this is not actually
     * unique then an exception will be thrown when Sakai attempts to register this broker).
     * (the global reference string will consist of the entity prefix and the local id)
     * 
     * @return the string that represents the globally unique prefix for an entity type
     */
    @Override
    public String getEntityPrefix()
    {
        logIfDebugEnabled( "getEntityPrefix()" );

        return ScormEntityProvider.ENTITY_PREFIX;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getSampleEntity()
    {
        logIfDebugEnabled( "getSampleEntity()" );

        return new ScormEntity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getHandledOutputFormats()
    {
        logIfDebugEnabled( "getHandledOutputFormats()" );

        return ScormEntityProvider.HANDLED_OUTPUT_FORMATS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> findEntityRefs( String[] prefixes, String[] names, String[] searchValues, boolean exactMatch )
    {
        logIfDebugEnabled( "findEntityRefs()" );

        String siteID = null;
        String userID = null;
        List<String> entityRefs = new ArrayList<>();

        // If the provided prefix is that of the SCORM prefix...
        if( ENTITY_PREFIX.equals( prefixes[0] ) )
        {
            // Get the siteID and userID
            for( int i = 0; i < names.length; i++ )
            {
                if( "context".equalsIgnoreCase( names[i] ) || "site".equalsIgnoreCase( names[i] ) )
                {
                    siteID = searchValues[i];
                }
                else if( "user".equalsIgnoreCase( names[i] ) || "userId".equalsIgnoreCase( names[i] ) )
                {
                    userID = searchValues[i];
                }
            }

            // If a siteID and userID are provided...
            if( siteID != null && userID != null )
            {
                try
                {
                    // If the siteID and userID are the same, it's really trying to access the user's My Workspace, so we need to prepend '~' to the siteID
                    if( siteID.equals( userID ) )
                    {
                        siteID = "~" + siteID;
                    }

                    // Get the site, verify it exists
                    Site site = siteService.getSite( siteID );
                    if( site != null )
                    {
                        // Check to make sure the current user has the 'scorm.configure' permission for the site
                        if( !securityService.unlock( userID, TOOL_CONFIG_PERM, siteService.siteReference( siteID ) ) )
                        {
                            // Log the message that this user doesn't have the permision for the site, return an empty list
                            LOG.error( "User (" + userID + ") does not have permission (" + TOOL_CONFIG_PERM + ") for site: " + siteID );
                            return entityRefs;
                        }

                        // Get the tool ID
                        String toolID = "";
                        Collection<ToolConfiguration> toolConfigs = site.getTools( TOOL_REG_NAME );
                        for( ToolConfiguration toolConfig : toolConfigs )
                        {
                            toolID = toolConfig.getId();
                        }

                        // Only continue if the tool ID is valid
                        if( StringUtils.isNotBlank( toolID ) )
                        {
                            // Get the content packages
                            List<ContentPackage> contentPackages = contentService.getContentPackages( siteID );
                            for( ContentPackage contentPackage : contentPackages )
                            {
                                String refString = "/" + ENTITY_PREFIX + "/" + 
                                                   siteID + ENTITY_PARAM_DELIMITER +
                                                   toolID + ENTITY_PARAM_DELIMITER +
                                                   contentPackage.getContentPackageId() + ENTITY_PARAM_DELIMITER +
                                                   contentPackage.getResourceId() + ENTITY_PARAM_DELIMITER +
                                                   contentPackage.getTitle();
                                entityRefs.add( refString );
                            }
                        }
                    }
                }
                catch( IdUnusedException ex )
                {
                    LOG.warn( "Can't find site with ID = " + siteID, ex );
                    throw new IllegalArgumentException( "Can't find site with ID = " + siteID, ex );
                }
            }
        }

        return entityRefs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getEntity( EntityReference ref )
    {
        logIfDebugEnabled( "getEntity()" );

        try
        {
            // If the reference is invalid, throw an exception and exit
            if( ref == null || StringUtils.isBlank( ref.getId() ) )
            {
                throw new IllegalArgumentException( "You must supply a valid EntityReference" );
            }

            // If the user has permission to launch SCORM modules in the current site, redirect them to the module
            ScormEntity entity = getScormEntity( ref.getId() );
            if( isCurrentUserLaunchAuth( entity.getSiteID() ) )
            {
                requestGetter.getResponse().sendRedirect( "/direct/" + ENTITY_PREFIX + "/" + entity.getID() + "/redirect" );
            }

            // Otherwise, redirect them to the HTML representation of the SCORM entity
            else
            {
                requestGetter.getResponse().sendRedirect( "/direct/" + ENTITY_PREFIX + "/" + entity.getID() + "/viewHTML" );
            }
        }
        catch( IOException ex )
        {
            LOG.error( ex );
        }

        return ref;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean entityExists( String id )
    {
        logIfDebugEnabled( "entityExists()" );

        // If the id is invalid, throw an exception and exit
        if( StringUtils.isBlank( id ) )
        {
            throw new IllegalArgumentException( "You must supply a valid ID" );
        }

        // Otherwise, attempt to get the entity
        else
        {
            // If the entity is null, it doesn't exist
            return getScormEntity( id ) != null;
        }
    }

    /**
     * Returns an HTML string that describes the ScormEntity in question;
     * takes into account authentication for viewing SCORM modules.
     * 
     * @param ref the EntityReference object requested
     * @return the HTML string describing the entity
     */
    @EntityCustomAction( action = "viewHTML", viewKey = EntityView.VIEW_SHOW )
    public Object getScormEntityAsHTML( EntityReference ref )
    {
        logIfDebugEnabled( "getScormEntityAsHTML()" );

        // Return the generated HTML
        return new ActionReturn( Formats.UTF_8, Formats.HTML_MIME_TYPE, createScormEntityHTML( (ScormEntity) getScormEntity( ref.getId() ) ) );
    }

    /**
     * Redirects the user who clicked on a SCORM entity link to the actual final generated
     * URL of the SCORM instance, provided the current user passes the validation/authentication
     * required for launching a SCORM module
     * 
     * @param vars the map of parameters returned from the EntityBroker (contains the toolID:contentPackageID:resourceID identifier)
     * @return the final generated URL of the SCORM instance
     */
    @EntityURLRedirect( "/{prefix}/{id}/redirect" )
    public String redirectScormEntity( Map<String, String> vars )
    {
        logIfDebugEnabled( "redirectScormEntity()" );

        // If the current user is able to launch a SCORM module, generate and return the final URL
        ScormEntity entity = (ScormEntity) getScormEntity( vars.get( "id" ) );
        if( isCurrentUserLaunchAuth( entity.getSiteID() ) )
        {
            return generateFinalScormURL( entity );
        }

        // Otherwise, redirect to the /viewHTML custom action (which handles the non-authorized presentation)
        else
        {
            return "/direct/" + ENTITY_PREFIX + "/" + entity.getID() + "/viewHTML";
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getProperties( String reference )
    {
        logIfDebugEnabled( "getProperties()" );

        // If the reference is invalid, throw an exception and exit
        Map<String, String> properties = new HashMap<>();
        if( StringUtils.isBlank( reference ) )
        {
            throw new IllegalArgumentException( "You must provide a valid reference string" );
        }
        else
        {
            // Get the entity by ID
            String id = reference.replaceAll( "/" + ENTITY_PREFIX + "/", "" );
            ScormEntity entity = getScormEntity( id );

            // If the entity is not null, get the properties
            if( entity != null )
            {
                properties.put( SCORM_ENTITY_PROP_SITE_ID, entity.getSiteID() );
                properties.put( SCORM_ENTITY_PROP_TOOL_ID, entity.getToolID() );
                properties.put( SCORM_ENTITY_PROP_CONTENT_PACKAGE_ID, entity.getContentPackageID() );
                properties.put( SCORM_ENTITY_PROP_RESOURCE_ID, entity.getResourceID() );
                properties.put( SCORM_ENTITY_PROP_TITLE, entity.getTitle() );
            }
        }

        return properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyValue( String reference, String name )
    {
        logIfDebugEnabled( "getPropertyValue()" );

        // Get the properties; if they're not null, get the named property requested
        String property = null;
        Map<String, String> properties = getProperties( reference );
        if( properties != null && properties.containsKey( name ) )
        {
            property = properties.get( name );
        }

        return property;
    }

    // *************************************************************
    // ******************* Private Utility Methods******************
    // *************************************************************

    /**
     * Determine if the current user should be able to launch the SCORM module, 
     * based on the 'scorm.launch' permission.
     * 
     * @param siteID the current site's internal ID
     * @return true/false if the user has the 'scorm.launch' permission in the current site
     */
    private boolean isCurrentUserLaunchAuth( String siteID )
    {
        logIfDebugEnabled( "isCurrentUserLaunchAuth()" );

        String userID = userDirectoryService.getCurrentUser().getId();
        return securityService.unlock( userID, TOOL_LAUNCH_PERM, siteService.siteReference( siteID ) );
    }

    /**
     * Get a ScormEntity object by ID (toolID:contentPackageID:resourceID:title)
     * 
     * @param entityID the packed ID reference string (toolID:contentPackageID:resourceID:title)
     * @return the ScormEntity object requested
     */
    private ScormEntity getScormEntity( String entityID )
    {
        logIfDebugEnabled( "getScormEntity()" );

        // Short circuit if an ID is not supplied
        ScormEntity entity = null;
        if( StringUtils.isBlank( entityID ) )
        {
            throw new IllegalArgumentException( "You must supply a valid reference string" );
        }
        else
        {
            String tokens[] = entityID.split( ScormEntityProvider.ENTITY_PARAM_DELIMITER );
            if( tokens.length == 5 )
            {
                String siteID           = tokens[0];
                String toolID           = tokens[1];
                String contentPackageID = tokens[2];
                String resourceID       = tokens[3];
                String title            = tokens[4];

                entity = new ScormEntity( siteID, toolID, contentPackageID, resourceID, title );
            }
            else
            {
                throw new IllegalArgumentException( "You must supply a valid reference string" );
            }
        }

        return entity;
    }

    /**
     * Creates an HTML representation for a given ScormEntity object.
     * 
     * @param entity the ScormEntity to describe via HTML
     * @return the generated HTML string based on the provided ScormEntity object
     */
    private String createScormEntityHTML( ScormEntity entity )
    {
        logIfDebugEnabled( "createScormEntityHTML()" );

        StringBuilder sb = new StringBuilder();
        sb.append( resourceLoader.getFormattedMessage( "htmlHeader", new Object[] { serverConfigurationService.getString( "skin.repo" ) + "/tool_base.css" } ) );

        // If the user is allowed to launch SCORM modules in the current site, generate the HTML to view the link
        if( isCurrentUserLaunchAuth( entity.getSiteID() ) )
        {
            sb.append( resourceLoader.getFormattedMessage( "htmlIframe", new Object[] { generateFinalScormURL( entity ) } ) );
        }

        // Otherwise, just build some HTML to tell the user thye're not allowed to launch SCORM modules in this site
        else
        {
            sb.append( resourceLoader.getFormattedMessage( "htmlH2", new Object[] { resourceLoader.getString( "authFailMsg" ) } ) );
        }

        // Return the generated HTML string
        sb.append(  resourceLoader.getString( "htmlFooter" ) );
        return sb.toString();
    }

    /**
     * Generates the final URL for a SCORM module entity, which includes the tool ID,
     * content package ID, resource ID and title.
     * 
     * @param entity the specific SCORM module the requester wants a link to
     * @return 
     */
    private String generateFinalScormURL( ScormEntity entity )
    {
        logIfDebugEnabled( "generateFinalScormURL()" );

        // Build and return the full URL to the specified SCORM module
        String url = serverConfigurationService.getServerUrl() + "/portal/tool/" +
                     entity.getToolID() + "?" + 
                     SCORM_PLAYER_PAGE_URL_PART + "&contentPackageId=" +
                     entity.getContentPackageID() + "&resourceId=" +
                     entity.getResourceID() + "&title=" +
                     entity.getTitle();
        return url;
    }

    /**
     * Utility method to avoid repeating this debug code in every method.
     * 
     * @param message the message to be logged
     */
    private void logIfDebugEnabled( String message )
    {
        if( LOG.isDebugEnabled() )
        {
            LOG.debug( message );
        }
    }

    // *************************************************************
    // ******************* Unimplemented Methods *******************
    // *************************************************************

    @Override public String     createEntity( EntityReference ref, Object entity, Map<String, Object> params )  { return null; }
    @Override public List<?>    getEntities ( EntityReference ref, Search search )                              { return null; }
    @Override public String[]   getHandledInputFormats()                                                        { return null; }
    @Override public void       updateEntity( EntityReference ref, Object entity, Map<String, Object> params )  {}
    @Override public void       deleteEntity( EntityReference ref, Map<String, Object> params )                 {}
    @Override public void       setPropertyValue( String reference, String name, String value )                 {}
}
