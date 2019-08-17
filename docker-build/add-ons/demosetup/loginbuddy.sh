#!/bin/bash

#
# For development purposes, this script generates a private key on the fly that uses multiple hostnames.
# SNI (Server Name Indication) is used to lookup the matching certificate (private key), simulating different servers.

# setting the SSL port to 443 if none was given
#
if [ -z "$SSL_PORT" ]
then
  printf "===============\n"
  printf "== Loginbuddy: Using default SSL port 443\n"
  printf "===============\n"
  SSL_PORT=443
fi

# generating a UUID as password for the generated keystore
#
UUID=$(cat /proc/sys/kernel/random/uuid)

# Create private key
#
keytool -genkey -alias loginbuddy -keystore /usr/local/tomcat/ssl/loginbuddy.p12 -storetype PKCS12 -keyalg RSA -storepass ${UUID} -keypass ${UUID} -validity 1 -keysize 2048 -dname "CN=local.loginbuddy.net" -ext san=dns:local.loginbuddy.net,dns:demoserver.loginbuddy.net,dns:democlient.loginbuddy.net

# Export the public certificates
#
keytool -export -alias loginbuddy -file /usr/local/tomcat/ssl/loginbuddy.crt -keystore /usr/local/tomcat/ssl/loginbuddy.p12 -storepass ${UUID}

# Import the certs as trusted certificates
#
keytool -importcert -alias loginbuddy -file /usr/local/tomcat/ssl/loginbuddy.crt -storepass changeit -keystore $JAVA_HOME/lib/security/cacerts -trustcacerts -noprompt

## Find the policy file that contains socket permissions and add them to the default catalina.policy file
# default is located here: /usr/local/tomcat/conf/catalina.policy
#
cat /usr/local/tomcat/webapps/ROOT/WEB-INF/classes/permissions.policy >> /usr/local/tomcat/conf/catalina.policy

# specifying that 'none' is invalid for JWT signature algorithms
#
export CATALINA_OPTS="${SYSTEM_PROPS} -Dorg.jose4j.jws.default-allow-none=false"

# replace @@variable@@ in server.xml with the real values
#
sed -i "s/@@sslport@@"/${SSL_PORT}/g /usr/local/tomcat/conf/server.xml
sed -i "s/@@sslpwd@@"/${UUID}/g /usr/local/tomcat/conf/server.xml

# overwrite the variables since they are not needed anywhere anymore
#
export SSL_PORT=
export UUID=

# run the original tomcat entry point command as specified in tomcat's Dockerfile
#
sh /usr/local/tomcat/bin/catalina.sh run -security