import jenkins.model.Jenkins
def instance = Jenkins.instance
instance.setCrumbIssuer(null)
