FROM tomcat:alpine

# We do not want to keep the default applications, remove them
#
RUN rm -rf /usr/local/tomcat/webapps/*

# add our own application as the one and only
#
# Default loginbuddy services
COPY net.loginbuddy.service/target/service-1.0.0 /usr/local/tomcat/webapps/ROOT

# - update 'catalina.policy' with required SocketPermissions
# - update 'server.xml' with unique passwords for accessing the private keys
# - update 'loginbuddy.net' generates a private key for the 'side-car' scenario. This can be self signed. Valid for 90 days
#
COPY docker-build/add-ons/server/catalina.policy /usr/local/tomcat/conf/catalina.policy
COPY docker-build/add-ons/server/server.xml /usr/local/tomcat/conf/server.xml

# Create directory for holding SSL keys
#
RUN mkdir /usr/local/tomcat/ssl

# create non-priviliged user (with its own group) to run tomcat
#
RUN addgroup tomcat
RUN adduser -SG tomcat tomcat
RUN chown -R tomcat:tomcat /usr/local/tomcat/

# Run the entrypoint script to run tomcat with security manager
#
CMD ["catalina.sh", "run", "-security"]