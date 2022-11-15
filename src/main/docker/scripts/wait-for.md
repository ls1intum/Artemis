# wait-for.sh

We are using wait-for.sh to poll other containers used in our docker compose setups as docker compose doesn't
provide a functionality to check the readiness of other containers out of the box.

*Source:*
https://github.com/Eficode/wait-for

*used version/release:*
v2.2.3

<!--
TODO: Rethink this approach and maybe use an approach like KIT which are using a combination of healthchecks and the
depends_on settings
-->

