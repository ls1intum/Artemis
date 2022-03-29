Known Issues
============

No user updates with Jenkins without password
---------------------------------------------
Currently, Jenkins doesn't allow to update the user details. We would have to delete and create the user with the new information.
We can only do this if we have the password, which we don't have unless the admin provides a new password as the passwords are hashed in our database.
If you update the user details in Artemis you have the following options if you use Jenkins:

a) Update the user manually in Jenkins. As admins rarely update the users themselves, this wouldn't be too much work.
b) Leave it as it is. You have to decide for yourself whether the user details in Jenkins are important.
c) Provide a password. **Not recommended!** You would need to provide the user with the new password which is itself a security issue and then the user has to change the password which might not be done reliably.

Important to know:

- Group/Permission updates get always delegated. They are separate from user details.
- If users change their password after a not delegated user change, the user change gets automatically applied when recreating the Jenkins user.
