def: testHW8

dist: all.tar clean
	chmod a+r all.tar

gen: gen.cpp
	g++ gen.cpp -o gen

all.tar: gen
	gen
	tar cf all.tar Makefile MANIFEST.MF USER* *.java

clean:
	/bin/rm -f gen *.class 141OS.jar USER* alt141OS.jar

141OS.jar: MainClass.java
	javac MainClass.java
	jar cmvf MANIFEST.MF 141OS.jar  *.class

testHW8: 141OS.jar
	java -jar 141OS.jar -1 -1 -1

testHW9: 141OS.jar
	java -jar 141OS.jar -26 -2 -3

alt141OS.jar: MainClass.java
	javac MainClass.java
	jar cvf alt141OS.jar *.class

alttest: alt141OS.jar
	java -cp alt141OS.jar MainClass
