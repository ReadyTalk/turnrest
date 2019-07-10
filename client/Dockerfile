FROM nginx:1.17.1-alpine
RUN rm /usr/share/nginx/html/index.html
COPY html/ /usr/share/nginx/html/
COPY default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
STOPSIGNAL SIGTERM
CMD ["nginx", "-g", "daemon off;"]
