stock-picker
============

How to use:

- Clone this repo
- Download OR-tools java into the libs/ortools folder - https://drive.google.com/folderview?id=0B2yUSpEp04BNdEU4QW5US1hvTzg&usp=sharing#list

NOTE: Only the lib folders content should be in the ortools folder.

On OSX I ran:
tar -xvzf Google.OrTools.java.MacOsX64.3750.tar.gz 
cp -r ~/Downloads/or-tools.MacOsX64/lib/ stock-picker/libs/ortools/

- Make sure everything runs correctly by running ./gradlew run
- Make a copy of this Account holdings Google Sheet and fill it out https://docs.google.com/spreadsheets/d/1FHmZpNUhMbcM2kIfxSCh28zVzac-0OOkn4cldMOrrnA/edit#gid=1156966902
- Download your copy of the Account holding Google Sheet using File -> Download as -> Microsoft Excel .xlsx
- Copy the downloaded .xlsx into the main repo folder, replacing solverTemplate.xlsx
- Run!
