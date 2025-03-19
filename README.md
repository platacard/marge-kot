# Marge Kot 🐱

Marge KOT is a bot written in Kotlin that helps automatically merge Merge Requests in GitLab when using semi-linear history or fast-forward merge strategies.

If your project requires the target branch to always be up-to-date before merging, Marge KOT will handle the rebase and automatic update of the MR before merging.

## Configuration
Before using, you need to configure the bot by setting the following environment variables:

- `MARGE_KOT_PROJECT_ID` — The GitLab project ID that the bot will work with. In GitLab CI/CD, this corresponds to `CI_PROJECT_ID`.
- `MARGE_KOT_AUTH_TOKEN` — The authentication token that allows the bot to interact with your repository:
    - Push commits to the Merge Request during a rebase;
    - Merge the updated Merge Request into the target branch.
- `MARGE_KOT_TARGET_BRANCH` — The target branch that the bot will work with. For example, if your Merge Requests are directed to `main`, specify `main`. Regex is not supported.
- `MARGE_KOT_BASE_API` — The base URL for interacting with your GitLab API. Example: `https://gitlab.com/api/v4/`.
- `MARGE_KOT_ADD_REQUEST_LOGS` — Enable logging of network requests and responses.

## Running

### In Docker
The bot comes with a `Dockerfile`, making it easy to run in a container, such as in CI/CD.

### Local Execution
To run the bot locally, you first need to build the JAR file:

```sh
gradle clean build
```

After a successful build, you can run the JAR using:

```sh
java -jar --enable-preview build/libs/marge-kot.jar
```
