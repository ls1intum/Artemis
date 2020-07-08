FROM jenkins/jenkins:lts
 
 LABEL description="Jenkins with maven, python3.6 and gcc libraries pre-installed for Artemis"
 
 USER root
 
 RUN apt update
 
 # Install Java and Maven dependencies
 RUN apt-get install -y maven
 RUN cd /usr/lib/jvm && \
     wget https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14%2B36.1_openj9-0.19.0/OpenJDK14U-jdk_x64_linux_openj9_14_36_openj9-0.19.0.tar.gz && \
     tar -zxf OpenJDK14U-jdk_x64_linux_openj9_14_36_openj9-0.19.0.tar.gz \
     && mv jdk-14+36 java-14-openjdk-amd64 \
     && rm OpenJDK14U-jdk_x64_linux_openj9_14_36_openj9-0.19.0.tar.gz
 RUN chown -R root:root /usr/lib/jvm/java-14-openjdk-amd64
 RUN JAVA_HOME="/usr/lib/jvm/java-14-openjdk-amd64" && export JAVA_HOME
 ENV JAVA_HOME /usr/lib/jvm/java-14-openjdk-amd64
 
 # Install C dependencies
 RUN apt install -y gcc gdb make libubsan0 liblsan0 libtsan0

  # Some packages need to be installed to avoid some known problems for python3.6, see: https://github.com/pyenv/pyenv/wiki/Common-build-problems
 RUN apt install -y make build-essential libssl-dev zlib1g-dev libbz2-dev \
 libreadline-dev libsqlite3-dev wget curl llvm libncurses5-dev libncursesw5-dev \
 xz-utils tk-dev libffi-dev liblzma-dev

 # Install Python3.6
 RUN wget https://www.python.org/ftp/python/3.6.9/Python-3.6.9.tgz && \
	 tar xvf Python-3.6.9.tgz \
	 && cd Python-3.6.9 \
	 && ./configure --enable-optimizations --enable-shared \
	 && make -j8 \
	 && make altinstall
 RUN apt install -y python3 python3-pip
 RUN cd ..
 RUN rm -r -f Python3.6.9/ && rm -f Python-3.6.9.tgz
 
 # Install pytest for python exercises
 RUN pip3 install -U pytest
 
 USER jenkins
