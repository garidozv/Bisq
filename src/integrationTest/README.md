# Compiler integration tests

These tests run the complete compiler pipeline for each fixture:

1. Compile `program.mj` with `rs.ac.bg.etf.pp1.Compiler`
2. Compare the generated _MicroJava_ object file with `expected.obj`
3. Run the object file with the bundled MicroJava runtime
4. Compare its output with `expected.out`

Run the suite with:

```text
./gradlew integrationTest
./gradlew check
```

## Test resources

Each test is a folder under `valid` or `invalid`:

```text
src/integrationTest/resources/
  valid/
    my-test/
      program.mj
      input.txt          optional
      expected.obj
      expected.out
      expected.disasm    optional
  invalid/
    my-invalid-test/
      program.mj
```

Valid tests compare the generated object file and program output. Invalid tests only need `program.mj` and must fail compilation. 
Add `input.txt` when the program reads input. Add `expected.disasm` only when disassembly should also be compared.

## Adding a test

Create a descriptive folder under `src/integrationTest/resources/valid` or
`src/integrationTest/resources/invalid`, then add `program.mj`.

For a valid test, run:

```text
./gradlew integrationTest
```

If expected files are missing, the test generates them and fails once. Review
the generated files and run the test again.
