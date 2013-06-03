Groovy Label Assignment plugin
==============================

Grooovy スクリプトでジョブが走るノードのラベル指定を動的に行う Jenkins プラグイン

これはなに？
------------

Groovy Label Assignment plugin は、ジョブの設定に「Groovy スクリプトで実行するノードを制限」を追加する [Jenkins](http://jenkins-ci.org/) プラグインです。

* Groovy スクリプトからの返却値を、ノードを指定するラベル式として評価します。
	* 「実行するノードを制限」や、「スレーブ」「ラベル式」などのマルチ構成プロジェクトで定義する軸での指定を上書きします。
	* String型以外の値が返った場合、`toString()`で文字列に変換します。
	* null や 空白文字列が返却された場合、既存のラベル式を上書きしません。
* Groovy スクリプトでは以下の変数がバインドされます。s
	* 「ビルドのパラメータ化」で定義したパラメータ。
	* マルチ構成プロジェクトで定義した軸。
	* プラグインによって定義される環境変数。
* 以下の場合、ビルドは開始しません(ビルド実行の操作が無視されます)。
	* Groovy Label Assignment が定義されているのに Groovy スクリプトが設定されていない場合。
	* Groovy スクリプトに文法エラーがあってコンパイルに失敗した場合。
	* Groovy スクリプトが実行時に例外を出した場合。

利用例1
-------

以下の状況を想定します。

* あるプロジェクトは、複数のプラットフォーム (arm, win, linux) でビルドする必要があります。
* 以下のスレーブが定義されています。
	
	|Node|Label|arm|win|linux|
	|:---|:----|:--|:--|:----|
	|win1|vs2010,armcc|O|O|X|
	|win2|armcc|X|O|X|
	|linux|gcc|X|X|O|

この場合、マルチ構成プロジェクトを使用して以下の設定にすることで実現出来ます。

* ユーザ定義の軸 "platform" を定義し、以下の値を設定する: arm, win, linux
* スレーブ軸 "slave" を定義し、以下の値を設定する: armcc, vs2010, gcc
* 組み合わせフィルター を以下のように定義する。
	
	```
	(platform == "arm" && slave=="armcc") || (platform == "win" && slave=="vs2010") || (platform == "linux" && slave=="gcc")
	```

Groovy Label Assignment プラグインを使用すると、以下のようにして実現出来ます。

* ユーザ定義の軸 "platform" を定義し、以下の値を設定する: arm, win, linux
* 「Groovy スクリプトで実行するノードを制限」で以下の Groovy スクリプトを定義する。
	
	```
	def labelMap = [
	    arm: "armcc",
	    win: "vs2010",
	    linux: "gcc",
	];
	return labelMap.get(binding.getVariables().get("platform"));
	```

使用例2
-------

開発者がジョブを実行することでソースツリーのビルドする環境を考えます。

* 開発者はビルドの実行を行う際にパラメータを指定することで、リリースビルドとスナップショットビルドを切り替えます。
* リリースビルドの場合、"RELEASE" ラベルが設定されているノードでビルドし、スナップショットビルドの場合、その他のノードを使用します。

Groovy Label Assignment プラグインを使用することで、以下のように実現出来ます。

* 「ビルドのパラメータ化」を指定する。
* 真偽値のパラメータ "release" を定義する。
* 「Groovy スクリプトで実行するノードを制限」で以下の Groovy スクリプトを定義する。
	
	```
	return (release == "true")?"RELEASE":"!RELEASE"
	```

制限事項
--------

* 一部の変数が Groovy スクリプトに正しくバインドされない場合があります。
	* 特定の種類のパラメータ
	* 特定のプラグインによって定義される環境変数。
	* Groovy Label Assignment が処理を行う時点ではまだビルドは開始していないため、ビルドの情報を参照して作成される変数が正しく処理されない場合があります。
* Groovy Label Assignment plugin が処理に失敗する以下の様な場合、ビルドは実行されません。処理失敗について調べるには Jenkins のシステムログを参照する必要があります。
	* Groovy スクリプトが定義されていない場合。
	* Groovy スクリプトに文法エラーがある場合。
	* Groovy スクリプトが実行時に例外を送出した場合。
		* 特に、バインドされていない変数を参照した場合に例外が発生します。マルチ構成プロジェクトで軸の値を参照する場合、親ビルドでは軸が定義されないため、以下のようにアクセスしてください。
			
			```
			binding.getVariables().get("variable-name");
			```
			
	* Groovy スクリプトが返却した値がラベル式として評価できなかった場合。

動作原理
--------

1. ビルドの実行時に、`GroovyLabelAssignmentQueueDecisionHandler` が呼ばれます。
2. `GroovyLabelAssignmentQueueDecisionHandler` は、 `GroovyLabelAssignmentProperty` がジョブに設定されている場合、それを呼び出します。
3. `EnvironmentContributingAction#buildEnvVars` を呼び出し、Groovy スクリプトにバインドする変数を取得します。
	* ビルドのパラメータもここで取得されます。
4. Groovy スクリプトにバインドする軸の値を取得します。
5. Groovy スクリプトを実行します。
6. Groovy スクリプトからの返却値をラベル式として評価します。
7. `LabelAssignmentAction` を使用してラベル式をジョブに割り当てます。
