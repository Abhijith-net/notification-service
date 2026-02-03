# Push This Project to GitHub

Follow these steps to create a GitHub repo and push this project.

## 1. Create a new repository on GitHub

1. Go to [github.com/new](https://github.com/new).
2. Set **Repository name** to `notification-service` (or any name you prefer).
3. Choose **Public** (or Private).
4. **Do not** initialize with a README, .gitignore, or license (this project already has them).
5. Click **Create repository**.

## 2. Push from your machine

From the project root (`notification-service/`):

```bash
# Initialize git (if not already done)
git init

# Stage all files
git add .

# First commit
git commit -m "Initial commit: plan + Spring Boot skeleton"

# Add your GitHub repo as remote (replace YOUR_USERNAME and REPO_NAME with yours)
git remote add origin https://github.com/YOUR_USERNAME/notification-service.git

# Or with SSH:
# git remote add origin git@github.com:YOUR_USERNAME/notification-service.git

# Push (use -u to set upstream)
git branch -M main
git push -u origin main
```

## 3. Iterate

After that, you can iterate as usual:

```bash
git add .
git commit -m "Your change description"
git push
```

Use **[NOTIFICATION_SERVICE_PLAN.md](NOTIFICATION_SERVICE_PLAN.md)** as the reference for implementation phases.
