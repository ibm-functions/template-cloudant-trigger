# template-cloudant-trigger

### Overview
You can use this template to deploy some IBM Cloud Functions assets for you.  The assets created by this template are described in the manifest.yaml file, which can be found at `template-cloudant-trigger/runtimes/your_language_choice/manifest.yaml`

The assets described by this template are a trigger to fire events when data is changed in an associated cloudant database, an action from the openwhisk cloudant package to read from cloudant, an action to print out the changed data, a sequence to tie the two actions together, and a rule that maps the trigger to the sequence.

When this template is deployed and associated with a cloudant database, it will print out changes made to the cloudant database.  The action assumes that there is a database of cats, each with a name and a color.

You can use the wskdeploy tool to deploy this asset yourself using the manifest and available code.

### Available Languages
This template is available in node.js, php, python & swift.
