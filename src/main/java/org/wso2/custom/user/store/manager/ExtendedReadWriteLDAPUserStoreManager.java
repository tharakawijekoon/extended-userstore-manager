package org.wso2.custom.user.store.manager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.api.Properties;
import org.wso2.carbon.user.api.Property;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.constants.UserCoreErrorConstants;
import org.wso2.carbon.user.core.ldap.LDAPConstants;
import org.wso2.carbon.user.core.ldap.ReadWriteLDAPUserStoreManager;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.user.core.util.UserCoreUtil;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class ExtendedReadWriteLDAPUserStoreManager extends ReadWriteLDAPUserStoreManager {
    private static Log log = LogFactory.getLog(ExtendedReadWriteLDAPUserStoreManager.class);
    private static ThreadLocal<Boolean> adduser = new ThreadLocal<Boolean>() {
        protected Boolean initialValue() {
            return false;
        }
    };
    private static final String USER_GROUP_CLAIM = "http://wso2.org/claims/userGroup";
    private static final String ADD_USER_SEARCH_FILTER = "AddUserSearchFilter";

    public ExtendedReadWriteLDAPUserStoreManager() {
        super();
    }

    public ExtendedReadWriteLDAPUserStoreManager(RealmConfiguration realmConfig, Map<String, Object> properties,
                                                   ClaimManager claimManager, ProfileConfigurationManager profileManager,
                                                   UserRealm realm, Integer tenantId) throws UserStoreException {

        super(realmConfig, properties, claimManager, profileManager, realm, tenantId);
        log.info("ExtendedReadWriteLDAPUserStoreManager initialized...");
    }

    public void doAddUser(String userName, Object credential, String[] roleList,
                          Map<String, String> claims, String profileName, boolean requirePasswordChange)
            throws UserStoreException {
        String externalRole = claims.get(USER_GROUP_CLAIM);
        if( externalRole != null && externalRole.trim().length() > 0) {
            externalRole = UserCoreUtil.removeDomainFromName(externalRole);
            if(log.isDebugEnabled()){
                log.debug(USER_GROUP_CLAIM + " has group : " + externalRole);
            }
            if (!doCheckExistingRole(externalRole)) {
                String message = String
                        .format(UserCoreErrorConstants.ErrorMessages.ERROR_CODE_EXTERNAL_ROLE_NOT_EXISTS.getMessage(),
                                externalRole);
                String errorCode = UserCoreErrorConstants.ErrorMessages.ERROR_CODE_EXTERNAL_ROLE_NOT_EXISTS.getCode();
                throw new UserStoreException(errorCode + " - " + message);
            }
            ArrayList<String> newList = new ArrayList<String>(Arrays.asList(roleList));
            newList.add(externalRole);
            roleList = newList.toArray(roleList);
        }
        try {
            adduser.set(true);
            super.doAddUser(userName, credential, roleList, claims, profileName, requirePasswordChange);
        } finally {
            adduser.set(false);
        }
    }

    protected String getNameInSpaceForUsernameFromLDAP(String userName) throws UserStoreException {
        if (!adduser.get()) {
            return super.getNameInSpaceForUsernameFromLDAP(userName);
        }
        String searchBase = null;
        String userSearchFilter = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        String addUserSearchFilter = realmConfig.getUserStoreProperty(ADD_USER_SEARCH_FILTER);
        if (addUserSearchFilter != null && addUserSearchFilter.trim().length() > 0) {
            userSearchFilter = addUserSearchFilter;
        }
        userSearchFilter = userSearchFilter.replace("?", escapeSpecialCharactersFilter(userName));
        String userDNPattern = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);
        if (userDNPattern != null && userDNPattern.trim().length() > 0) {
            String[] patterns = userDNPattern.split("#");
            for (String pattern : patterns) {
                searchBase = MessageFormat.format(pattern, escapeSpecialCharactersDN(userName));
                String userDN = getNameInSpaceForUserName(userName, searchBase, userSearchFilter);
                // check in another DN pattern
                if (userDN != null) {
                    return userDN;
                }
            }
        }
        searchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        return getNameInSpaceForUserName(userName, searchBase, userSearchFilter);
    }

    public Properties getDefaultUserStoreProperties() {
        Properties properties = super.getDefaultUserStoreProperties();
        properties.setMandatoryProperties(ExtendedReadWriteLDAPUserStoreConstants.EXTENDED_RWLDAP_USERSTORE_PROPERTIES.toArray
                (new Property[ExtendedReadWriteLDAPUserStoreConstants.EXTENDED_RWLDAP_USERSTORE_PROPERTIES.size()]));
        properties.setOptionalProperties(ExtendedReadWriteLDAPUserStoreConstants.EXTENDED_OPTINAL_RWLDAP_USERSTORE_PROPERTIES.toArray
                (new Property[ExtendedReadWriteLDAPUserStoreConstants.EXTENDED_OPTINAL_RWLDAP_USERSTORE_PROPERTIES.size()]));
        return properties;
    }

    /**
     * Escaping ldap search filter special characters in a string
     *
     * @param dnPartial String to replace special characters of
     * @return
     */
    private String escapeSpecialCharactersFilter(String dnPartial) {
        boolean replaceEscapeCharacters = true;
        dnPartial.replace("\\*", "*");

        String replaceEscapeCharactersAtUserLoginString = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_REPLACE_ESCAPE_CHARACTERS_AT_USER_LOGIN);

        if (replaceEscapeCharactersAtUserLoginString != null) {
            replaceEscapeCharacters = Boolean
                    .parseBoolean(replaceEscapeCharactersAtUserLoginString);
            if (log.isDebugEnabled()) {
                log.debug("Replace escape characters configured to: "
                        + replaceEscapeCharactersAtUserLoginString);
            }
        }
        //TODO: implement character escaping for *

        if (replaceEscapeCharacters) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dnPartial.length(); i++) {
                char currentChar = dnPartial.charAt(i);
                switch (currentChar) {
                    case '\\':
                        sb.append("\\5c");
                        break;
                    case '*':
                        sb.append("\\2a");
                        break;
                    case '(':
                        sb.append("\\28");
                        break;
                    case ')':
                        sb.append("\\29");
                        break;
                    case '\u0000':
                        sb.append("\\00");
                        break;
                    default:
                        sb.append(currentChar);
                }
            }
            return sb.toString();
        } else {
            return dnPartial;
        }
    }

    /**
     * Escaping ldap DN special characters in a String value
     *
     * @param text String to replace special characters of
     * @return
     */
    private String escapeSpecialCharactersDN(String text) {
        boolean replaceEscapeCharacters = true;
        text.replace("\\*", "*");

        String replaceEscapeCharactersAtUserLoginString = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_REPLACE_ESCAPE_CHARACTERS_AT_USER_LOGIN);

        if (replaceEscapeCharactersAtUserLoginString != null) {
            replaceEscapeCharacters = Boolean
                    .parseBoolean(replaceEscapeCharactersAtUserLoginString);
            if (log.isDebugEnabled()) {
                log.debug("Replace escape characters configured to: "
                        + replaceEscapeCharactersAtUserLoginString);
            }
        }

        if (replaceEscapeCharacters) {
            StringBuilder sb = new StringBuilder();
            if ((text.length() > 0) && ((text.charAt(0) == ' ') || (text.charAt(0) == '#'))) {
                sb.append('\\'); // add the leading backslash if needed
            }
            for (int i = 0; i < text.length(); i++) {
                char currentChar = text.charAt(i);
                switch (currentChar) {
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case ',':
                        sb.append("\\,");
                        break;
                    case '+':
                        sb.append("\\+");
                        break;
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '<':
                        sb.append("\\<");
                        break;
                    case '>':
                        sb.append("\\>");
                        break;
                    case ';':
                        sb.append("\\;");
                        break;
                    case '*':
                        sb.append("\\2a");
                        break;
                    default:
                        sb.append(currentChar);
                }
            }
            if ((text.length() > 1) && (text.charAt(text.length() - 1) == ' ')) {
                sb.insert(sb.length() - 1, '\\'); // add the trailing backslash if needed
            }
            if (log.isDebugEnabled()) {
                log.debug("value after escaping special characters in " + text + " : " + sb.toString());
            }
            return sb.toString();
        } else {
            return text;
        }

    }
}
