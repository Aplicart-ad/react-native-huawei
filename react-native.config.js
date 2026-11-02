module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath: 'import expo.modules.huawei.HuaweiPackage;',
        packageInstance: 'new HuaweiPackage()',
      },
      ios: null,
    },
  },
};
