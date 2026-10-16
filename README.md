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
- `MARGE_KOT_AUTO_MERGE_LABEL` — The label that the bot will use to find Merge Requests for auto-merging. If set, the bot will assign and merge MRs with this label when they are ready.
- `MARGE_KOT_MERGE_TIMEOUT_MINUTES` — How long one merge attempt (rebase and waiting for the pipeline) may take. When the target branch moves and the bot rebases, the next attempt gets a fresh timeout. One Merge Request may take at most three times this value in total. Must be a positive whole number; the bot refuses to start otherwise. Default: `90`.

## When the bot gives up
The bot works on one Merge Request at a time. It leaves a comment, unassigns itself and moves on to the next Merge Request when:

- the MR is a draft, lacks approvals, has unresolved blocking discussions or conflicts;
- the pipeline failed or was canceled;
- the pipeline was skipped (for example, the head commit has `[skip ci]`) while the project requires a successful pipeline and does not treat skipped pipelines as successful;
- one attempt takes longer than `MARGE_KOT_MERGE_TIMEOUT_MINUTES`, e.g. the pipeline waits for a manual job or never starts;
- the MR is not merged within three times `MARGE_KOT_MERGE_TIMEOUT_MINUTES`;
- the target branch moved during the pipeline more than 5 times while processing this MR;
- a rebase does not finish within 10 minutes or fails repeatedly.

## Running

### In Docker
The bot comes with a `Dockerfile`, making it easy to run in a container, such as in CI/CD.

### Local Execution
To run the bot locally, you first need to build the JAR file:

```sh
./gradlew clean build
```

After a successful build, you can run the JAR using:

```sh
java -jar --enable-preview build/libs/marge-kot.jar
```
