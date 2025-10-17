---
id: security
title: Security Configuration
sidebar_label: Security
---

import Callout from "../../src/components/callout/callout";
import {CalloutVariant} from "../../src/components/callout/callout.types";

# Security

Artemis uses configuration files that contain **default passwords and secrets**.  
These **must** be overridden in your own configuration or via environment variables.

```yaml
artemis:
    user-management:
        internal-admin:
            username: "artemis-admin"
            password: "artemis-admin"
    version-control:
        build-agent-git-username: "buildagent_user"
        build-agent-git-password: "buildagent_password"
jhipster:
    security:
        authentication:
            jwt:
                base64-secret: ""
    registry:
        password: "change-me"
   ```

<Callout variant={CalloutVariant.danger}>
    <p>⚠️ Always replace default credentials before deploying. Failing to do so exposes your system to serious security risks.</p>
</Callout>

## SSH Access

SSH must be configured correctly for programming exercise repositories in the integrated lifecycle setup.

### Generate Key Pairs

``` bash
ssh-keygen -t rsa -b 4096 -f ~/artemis_ssh/id_rsa
ssh-keygen -t ed25519 -f ~/artemis_ssh/id_ed25519
```

### Distribute Keys via Ansible

``` yaml
- name: Distribute SSH keys
  hosts: all
  vars:
    key_dir: "/path/to/keys"
  tasks:
    - name: Copy RSA key
      copy:
        src: "{{ key_dir }}/id_rsa"
        dest: "~/.ssh/id_rsa"
        mode: '0600'
```

### Enable SSH Routing via Nginx

``` nginx
stream {
  server {
    listen 7921;
    proxy_pass 127.0.0.1:7921;
  }
}
```

### Restart Nginx

``` bash
sudo systemctl restart nginx
```

<Callout variant={CalloutVariant.info}>
    <p>Ensure all nodes use the same SSH key set and restrict access to configuration files for system users only.</p>
</Callout>
```
