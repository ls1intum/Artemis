# use a node image as the base image and name it 'build' for
# later reference
FROM node:20-alpine AS build

# set the working directory to /app
WORKDIR /app
# copy the current directory contents into the container at /app
COPY . .

# Uncomment the following lines to limit the number of concurrent sockets in npm. This is a workaround for the issue with npm 10.5.0
# update npm to the latest version. Version 10.5.0 ignores the maxsockets setting
#RUN npm install -g npm@latest
# print npm version to the console
#RUN echo $(npm -v)
# limit maxsockets to 5 in npm
#RUN npm config set maxsockets 5
# print maxsockets value to host console
#RUN echo $(npm config get maxsockets)

# install dependencies, matching package-lock.json
RUN npm install
# build the app
RUN npm run build


# Use the latest version of the official Nginx image as the base image
FROM nginx:1.23 AS proxy
# copy the built application from the build stage to the nginx html
# directory
COPY --from=build /app/build/resources/main/static /usr/share/nginx/html

# nginx config will be copied in the docker-compose file
