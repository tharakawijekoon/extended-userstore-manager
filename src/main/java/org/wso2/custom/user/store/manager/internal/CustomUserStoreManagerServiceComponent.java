package org.wso2.custom.user.store.manager.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.custom.user.store.manager.ExtendedActiveDirectoryUserStoreManager;
import org.wso2.custom.user.store.manager.ExtendedReadWriteLDAPUserStoreManager;

/**
 * OSGI service component for the custom user store manager bundle.
 */
@Component(
        name = "org.wso2.custom.user.store.manager.internal.CustomUserStoreManagerServiceComponent",
        immediate = true
)
public class CustomUserStoreManagerServiceComponent {

    private static final Log log = LogFactory.getLog(CustomUserStoreManagerServiceComponent.class);
    private static RealmService realmService;

    public static RealmService getRealmService() {
        return realmService;
    }

    @Reference(
            name = "user.realmservice.default",
            service = org.wso2.carbon.user.core.service.RealmService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRealmService")
    protected void setRealmService(RealmService rlmService) {

        if (log.isDebugEnabled()) {
            log.debug("Setting the Realm Service");
        }
        realmService = rlmService;
    }

    protected void unsetRealmService(RealmService rlmService) {

        if (log.isDebugEnabled()) {
            log.debug("Unset the Realm Service.");
        }
        realmService = null;
    }

    @Activate
    protected void activate(ComponentContext componentContext) {

        try {
            BundleContext bundleContext = componentContext.getBundleContext();
            bundleContext.registerService(UserStoreManager.class.getName(),
                    new ExtendedActiveDirectoryUserStoreManager(), null);
            bundleContext.registerService(UserStoreManager.class.getName(),
                    new ExtendedReadWriteLDAPUserStoreManager(), null);
            if (log.isDebugEnabled()) {
                log.debug("Custom user store manager component activated successfully.");
            }
        } catch (Throwable e) {
            log.error("Error while activating custom user store manager module.", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext ctxt) {
        if (log.isDebugEnabled()) {
            log.debug("Custom user store manager component is deactivated ");
        }
    }
}
