# ============================================================
#  build/paths.psd1 -- project path registry (ONE-LINE-ASCII SAFE)
#
#  Why this file looks unusual:
#    The project keeps its top-level folders in Chinese (01-wendang, 03-yuanma,
#    ...) for readability. Those names are non-ASCII characters. Windows
#    PowerShell 5.1 cannot reliably parse a *.psd1 that contains non-ASCII
#    text, and Import-PowerShellDataFile rejects computed values in BOTH 5.1
#    and 7 with error "Cannot generate ... ScriptBlock evaluating dynamic
#    expressions".
#
#    Solution used here:
#      * every Chinese character is written as an ASCII escape, e.g.
#        '03-' + [char]0x6E90 + [char]0x7801   ->   03-yuanma
#      * the file is loaded by DOT-SOURCING (see verify-environment.ps1),
#        which evaluates the expressions happily in both PowerShell versions.
#
#    Keep this file 100% ASCII if you edit it.
# ============================================================
@{
    Docs         = '01-' + [char]0x6587 + [char]0x6863
    Design       = '02-' + [char]0x8BBE + [char]0x8BA1 + [char]0x6587 + [char]0x6863
    Source       = '03-' + [char]0x6E90 + [char]0x7801
    Test         = '04-' + [char]0x6D4B + [char]0x8BD5 + [char]0x4E0E + [char]0x5B9E + [char]0x9A8C
    Deploy       = '05-' + [char]0x90E8 + [char]0x7F72 + [char]0x4E0E + [char]0x8FD0 + [char]0x7EF4
    Resource     = '06-' + [char]0x8D44 + [char]0x6E90
    ProjectPlan  = '01-' + [char]0x6587 + [char]0x6863 + '\00-' + [char]0x9879 + [char]0x76EE + [char]0x7BA1 + [char]0x7406 + '\' + [char]0x6BD5 + [char]0x4E1A + [char]0x8BBE + [char]0x8BA1 + [char]0x603B + [char]0x4F53 + [char]0x8BA1 + [char]0x5212 + [char]0x4E0E + [char]0x5B9E + [char]0x65BD + [char]0x65B9 + [char]0x6848 + '.md'
    Board        = '01-' + [char]0x6587 + [char]0x6863 + '\03-' + [char]0x8FDB + [char]0x5EA6 + [char]0x68C0 + [char]0x67E5 + '\' + [char]0x8FDB + [char]0x5EA6 + [char]0x770B + [char]0x677F + '.md'
    SpecSummary  = '01-' + [char]0x6587 + [char]0x6863 + '\99-' + [char]0x683C + [char]0x5F0F + [char]0x6A21 + [char]0x677F + '\' + [char]0x8BBA + [char]0x6587 + [char]0x64B0 + [char]0x5199 + [char]0x89C4 + [char]0x8303 + '-' + [char]0x8981 + [char]0x70B9 + [char]0x6458 + [char]0x5F55 + '.md'
    References   = '01-' + [char]0x6587 + [char]0x6863 + '\06-' + [char]0x6587 + [char]0x732E + [char]0x4E0E + [char]0x7EFC + [char]0x8FF0 + '\' + [char]0x53C2 + [char]0x8003 + [char]0x6587 + [char]0x732E + [char]0x6E05 + [char]0x5355 + '.md'
    AlgorithmDir = '03-' + [char]0x6E90 + [char]0x7801 + '\algorithm'
    ExperimentPy = '03-' + [char]0x6E90 + [char]0x7801 + '\algorithm\experiments.py'
    ModelPy      = '03-' + [char]0x6E90 + [char]0x7801 + '\algorithm\wmsopt\model.py'
    SlottingPy   = '03-' + [char]0x6E90 + [char]0x7801 + '\algorithm\wmsopt\slotting.py'
    PickingPy    = '03-' + [char]0x6E90 + [char]0x7801 + '\algorithm\wmsopt\picking.py'
    BackendPom   = '03-' + [char]0x6E90 + [char]0x7801 + '\backend\pom.xml'
    ExpSummary   = '04-' + [char]0x6D4B + [char]0x8BD5 + [char]0x4E0E + [char]0x5B9E + [char]0x9A8C + '\03-' + [char]0x7B97 + [char]0x6CD5 + [char]0x5BF9 + [char]0x6BD4 + [char]0x5B9E + [char]0x9A8C + '\' + [char]0x5B9E + [char]0x9A8C + [char]0x7ED3 + [char]0x679C + [char]0x6C47 + [char]0x603B + '.md'
    SchemaSql    = '05-' + [char]0x90E8 + [char]0x7F72 + [char]0x4E0E + [char]0x8FD0 + [char]0x7EF4 + '\sql\01-schema.sql'
}
