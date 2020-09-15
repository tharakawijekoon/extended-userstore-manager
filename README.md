# extended-userstore-manager
AD/LDAP Userstore managers for adding user to group with SCIM2 add user operation. SCIM2 has the limitation which stops you from adding a user to a group at the time the user is created. These extended userstore managers would allow you to get past this limitation.

At the time of user creation you would be able to send the group information as a claim in the request. The user store manager would read the group off of the claim and add the user to that group during the SCIM create user operation.

## Build
Execute the following command to build the project

```mvn clean install```

## Deploy

Copy and place the built JAR artifact from the /target/org.wso2.custom.user.store.manager-1.0.0.jar to the <IS_HOME>/repository/components/dropins directory. 

The group to which the user should be added is sent as a claim in the scim2 request, modify the scim2 schema extention to add this claim by following the steps below.

Open the file <IS-HOME>/repository/conf/scim2-schema-extension.config.
Add the following configuration at the end of the file, before the last element of the JSON array.
```
{
"attributeURI":"urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:userGroup",
"attributeName":"userGroup",
"dataType":"string",
"multiValued":"false",
"description":"The inital group of user",
"required":"false",
"caseExact":"false",
"mutability":"readwrite",
"returned":"default",
"uniqueness":"none",
"subAttributes":"null",
"canonicalValues":[],
"referenceTypes":[]
},
```
Add userGroup to the subAttributes element of the last element of the JSON array, as shown below.
```
"subAttributes":"verifyEmail askPassword employeeNumber costCenter organization division department manager userGroup",
```
Save the file and start Identity server.

Log in to the management console and add a local claim for the userGroup, "http://wso2.org/claims/userGroup". This claim will be read from the user store manager.(make sure to add the proper mapped attribute for the AD/LDAP)
<img width="1407" alt="Screenshot 2020-09-16 at 2 50 42 AM" src="https://user-images.githubusercontent.com/47600906/93267724-bc6d7a80-f7c9-11ea-88fa-58452b996bbf.png">
Add SCIM Claim Mapping
<img width="1002" alt="Screenshot 2020-09-16 at 3 10 25 AM" src="https://user-images.githubusercontent.com/47600906/93268063-3d2c7680-f7ca-11ea-95b1-36321a78c771.png">

In order to use the new userstore manager you can change the class name of the existing userstore file located at <IS_HOME>/repository/deployment/server/userstores/\<USERSTORE-DOMAIN\>.xml to either of the following classes, depending on the userstore type (AD/LDAP).

```<UserStoreManager class="org.wso2.custom.user.store.manager.ExtendedActiveDirectoryUserStoreManager">```

```<UserStoreManager class="org.wso2.custom.user.store.manager.ExtendedReadWriteLDAPUserStoreManager">```

Or a new userstore can be added from the management console.
<img width="1092" alt="Screenshot 2020-09-16 at 2 09 53 AM" src="https://user-images.githubusercontent.com/47600906/93269231-3d2d7600-f7cc-11ea-936a-d6f16471ded2.png">

For adding the user a wide search filter should be defined without any hardcoded groups, add this new search filter property to the <IS_HOME>/repository/deployment/server/userstores/\<USERSTORE-DOMAIN\>.xml in the following format.

```<Property name="AddUserSearchFilter">(&amp;(objectClass=user)(cn=?))</Property>```

## Run

Now you should be able to add the user to a group with scim2 add user operation. specify the group in the userGroup attribute in the request as shown below in the sample request.

```
curl -k --location --request POST 'https://localhost:9443/scim2/Users' \
--header 'Content-Type: application/json' \
--header 'Authorization: Basic YWRtaW46YWRtaW4=' \
--data-raw '{
        "schemas": [
                "urn:ietf:params:scim:schemas:core:2.0",
                "urn:ietf:params:scim:schemas:core:2.0:User",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
        ],
        "userName":"SECONDARY/SCIM_Test4",
        "password": "Password@1",
        "emails": [
                {
                        "primary": true,
                        "value": "test.test@test.com"
                }
        ],
        "EnterpriseUser": {
                "entityID": "11111111",
                "userGroup": "groupone"
    }
}'
```
Note: Users can only be added to groups of the userstore. Internal and Application roles are not supported.
