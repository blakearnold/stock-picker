Stock Picker
============

Stock Picker helps allocate funds across multiple brokerage accounts.

It allocates funds accross 401k, IRA, taxable accounts, HSA, etc. Optimially finding an allocation that:
1. Has a risk tolerance defined by the user
2. Minimizes tax liability
3. Minimizes management fees

The calculations are done offline, and the results can then be executed by the account holder.

## Under the hood
Stock picker uses [OR-tools Glop](https://developers.google.com/optimization/lp/glop) linear solver.

Current asset value is calculated via Google Sheets [Google Finance](https://support.google.com/docs/answer/3093281?hl=en) functions. The [Sheet](https://docs.google.com/spreadsheets/d/1FHmZpNUhMbcM2kIfxSCh28zVzac-0OOkn4cldMOrrnA/edit#gid=1156966902) is then downloaded by the user and parsed by Stock Picker.



## How to use

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
