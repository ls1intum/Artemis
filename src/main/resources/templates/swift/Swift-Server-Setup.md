# Bamboo Setup

# Docker Container
Start the container by executing:  
`docker exec --user="root" -it artemis_bamboo /bin/bash`  

## Install Swift on Ubuntu 18.04
Setup-Guide: `https://medium.com/@gigmuster/install-swift-5-0-on-ubuntu-18-04-86f6b96654`  

### Install clang
Run  
`apt-get install clang`

### Install libcurl
Run  
`apt-get install libcurl3 libpython2.7 libpython2.7-dev`

### libcurl4
When getting the error "version 'CURL_OPENSSL_4' not found" run  
`apt-get install libcurl4-openssl-dev`

### Download swift
Before running the below code, change to the directory which you want to download by using “cd” command.  
Copy the url of the latest tar file from `https://swift.org/download/#releases`.
Then download it via wget.  
For example: `wget https://swift.org/builds/swift-5.3-release/ubuntu1804/swift-5.3-RELEASE/swift-5.3-RELEASE-ubuntu18.04.tar.gz`  

### Extract tar file
Extract via `tar xzf swift-5.3-RELEASE/swift-5.3-RELEASE-ubuntu18.04.tar.gz`  

### Move extracted files
Move the extracted file to the user’s “share” directory.  
`mv swift-5.3-RELEASE-ubuntu18.04 /usr/share/swift`

### Update $PATH
Set the Swift path on the system’s PATH environment variable:  
`echo "export PATH=$PATH:/usr/share/swift/usr/bin" >> ~/.bashrc`  

Then, use “source” command to reload “~/.bashrc”.  
`source  ~/.bashrc`

### Check Swift version
Check the correctly installed version via: `swift --version`.

## Update $PATH env
### In container (not working as shell instead of bin/bash is executed)
README: `https://opensource.com/article/17/6/set-path-linux`  

Update $PATH of bamboo-user. Start the container with:  
`docker exec --user="bamboo" -it artemis_bamboo /bin/bash`  

Append following to ~/.bashrc:  
`echo "export PATH=$PATH:/usr/share/swift/usr/bin" >> ~/.bashrc`

# Bamboo Build Plan 
## Create Tasks 
Go to Plan Configuration > Default Job > Tasks  
- Create default task to checkout repos "tests and ${studentParentWorkingDirectoryName}"
- Create a task to build the swift project
  - Name the task `Build swift`.
  - Interpreter: `Shell`
  - Script location: `inline`
  - Body: `swift build`
  - Enter the following in the Environment variables:  
  `PATH=${system.PATH}:/usr/share/swift/usr/bin`
- Create a task to execute the swift project
  - Name the task `Execute swift`.
  - Interpreter: `Shell`
  - Script location: `inline`
  - Body: `swift run`
  - Enter the following in the Environment variables:  
  `PATH=${system.PATH}:/usr/share/swift/usr/bin`
