FROM alpine:3.22

RUN apk add --no-cache bash coreutils iptables tar

USER 0
WORKDIR /var/tmp

CMD ["sh", "-c", "while true; do sleep 3600; done"]
