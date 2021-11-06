FROM openjdk:17-slim-bullseye

# Install required dependencies
RUN apt-get update && apt-get install -y pngquant jpegoptim && apt-get clean && rm -rf /var/lib/apt/lists/*