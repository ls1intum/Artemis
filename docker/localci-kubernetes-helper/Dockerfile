FROM alpine:3.22

# ip6tables is a separate package on Alpine; without it the network isolation init container
# cannot apply any IPv6 rules and builds requested with network mode "none" stay reachable over IPv6.
RUN apk add --no-cache bash coreutils ip6tables iptables tar

# No USER directive on purpose: the Kubernetes Job spec sets the effective user per container.
# The helper and the isolation container both run as UID 0 because they share the workspace volume
# with a builder container that usually runs as root, and the isolation container needs NET_ADMIN.
WORKDIR /var/tmp

# nosemgrep: dockerfile.security.missing-user.missing-user
CMD ["sh", "-c", "while true; do sleep 3600; done"]
